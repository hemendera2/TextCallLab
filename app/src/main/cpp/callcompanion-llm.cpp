#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex gate;
llama_model * model = nullptr;
const llama_vocab * vocab = nullptr;
bool backend_initialized = false;

static void push_tokens(llama_batch_ext *batch, const llama_token *tokens,
                        int size, int start, bool emit_final, int total) {
    llama_batch_ext_clear(batch);
    for (int i=0;i<size;i++) {
        const int index=llama_batch_ext_add_token(batch, 0, tokens[i]);
        if (index < 0) return;
        const llama_pos pos=start+i;
        llama_batch_ext_set_pos(batch, index, &pos);
        if (emit_final && start+i == total-1) {
            llama_batch_ext_set_output_logits(batch, index, true);
        }
    }
}
static std::string decode(const std::string &prompt, int output_limit) {
    if (!model || !vocab) return "";
    std::vector<llama_token> prompt_tokens(1700);
    int n=llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(),
                         prompt_tokens.data(), (int)prompt_tokens.size(), true, true);
    if (n<=0 || n>1300) return "";
    prompt_tokens.resize((size_t)n);

    llama_context_params cp=llama_context_default_params();
    cp.n_ctx=1536;
    cp.n_batch=256;
    cp.n_ubatch=128;
    cp.n_threads=4;
    cp.n_threads_batch=4;
    cp.no_perf=true;
    llama_context *ctx=llama_init_from_model(model, cp);
    if (!ctx) return "";

    llama_batch_ext *batch=llama_batch_ext_init(ctx);
    if (!batch) { llama_free(ctx); return ""; }
    int position=0;
    bool ok=true;
    for (int offset=0;offset<n;offset+=128) {
        int count=std::min(128,n-offset);
        push_tokens(batch,prompt_tokens.data()+offset,count,offset,true,n);
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE,batch)!=0) {
            ok=false; break;
        }
        position+=count;
    }
    std::string output;
    if (ok) {
        llama_sampler_chain_params sp=llama_sampler_chain_default_params();
        llama_sampler *sampler=llama_sampler_chain_init(sp);
        llama_sampler_chain_add(sampler,llama_sampler_init_top_k(32));
        llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.60f));
        llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        for (int i=0;i<std::max(1,std::min(output_limit,80));i++) {
            llama_token next=llama_sampler_sample(sampler,ctx,-1);
            if (llama_vocab_is_eog(vocab,next)) break;
            char piece[2048];
            int length=llama_token_to_piece(vocab,next,piece,sizeof(piece),0,true);
            if (length>0) {
                output.append(piece,(size_t)length);
                if (output.find("<|im_end|>") != std::string::npos) break;
                if (output.size()>550) break;
            }
            if (position+1 >= 1536) break;
            push_tokens(batch,&next,1,position,false,1);
            llama_batch_ext_set_output_logits(batch,0,true);
            if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE,batch)!=0) break;
            position++;
        }
        llama_sampler_free(sampler);
    }
    llama_batch_ext_free(batch);
    llama_free(ctx);
    return output;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_in_textcall_lab_LocalModel_nativeLoad(JNIEnv *env,jclass,jstring path) {
    std::lock_guard<std::mutex> lock(gate);
    if (model) return env->NewStringUTF("");
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
    }
    const char *utf=env->GetStringUTFChars(path,nullptr);
    if (!utf) return env->NewStringUTF("No path");
    std::string filename(utf);
    env->ReleaseStringUTFChars(path,utf);
    llama_model_params mp=llama_model_default_params();
    mp.n_gpu_layers=0;
    mp.load_mode=LLAMA_LOAD_MODE_MMAP;
    model=llama_model_load_from_file(filename.c_str(),mp);
    if (!model) return env->NewStringUTF("llama.cpp could not load GGUF; verify architecture, file and available RAM");
    vocab=llama_model_get_vocab(model);
    if (!vocab) {
        llama_model_free(model);
        model=nullptr;
        return env->NewStringUTF("No tokenizer found in GGUF");
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_in_textcall_lab_LocalModel_nativeGenerate(JNIEnv *env,jclass,jstring prompt,jint limit) {
    std::lock_guard<std::mutex> lock(gate);
    if (!model) return env->NewStringUTF("");
    const char *bytes=env->GetStringUTFChars(prompt,nullptr);
    if (!bytes) return env->NewStringUTF("");
    std::string input(bytes);
    env->ReleaseStringUTFChars(prompt,bytes);
    std::string output=decode(input,(int)limit);
    return env->NewStringUTF(output.c_str());
}

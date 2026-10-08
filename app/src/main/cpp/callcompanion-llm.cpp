#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex gate;
std::atomic<bool> cancel_requested{false};
std::atomic<int> phase{0}; // 0 idle, 1 tokenizing, 2 prefill, 3 decoding, 4 timeout, 5 cancelled, 6 error
std::atomic<int> prefill_done{0}, prefill_total{0}, decoded_tokens{0};
llama_model *model=nullptr;
llama_context *context=nullptr;
llama_batch_ext *batch=nullptr;
const llama_vocab *vocab=nullptr;
bool backend_initialized=false;
constexpr int CONTEXT_TOKENS=640;
constexpr int MAX_PROMPT=520;
constexpr int MAX_OUTPUT=32;
constexpr auto MAX_CPU_TIME=std::chrono::seconds(12);
std::atomic<int64_t> abort_deadline_ns{0};
std::atomic<int> cpu_graph_stage{0};
using Clock=std::chrono::steady_clock;

int64_t now_ns() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        Clock::now().time_since_epoch()).count();
}
// llama.cpp's CPU graph invokes this DURING llama_process, not merely
// between batch calls. Without it a long first batch ignored our timeout.
bool abort_cpu_graph(void *) {
    if (cancel_requested.load(std::memory_order_relaxed)) {
        phase.store(5); return true;
    }
    const int64_t deadline=abort_deadline_ns.load(std::memory_order_relaxed);
    if (deadline>0 && now_ns()>deadline) {
        phase.store(4); return true;
    }
    return false;
}

bool terminated(Clock::time_point start) {
    if (cancel_requested.load()) { phase.store(5); return true; }
    if (Clock::now()-start>MAX_CPU_TIME) { phase.store(4); return true; }
    return false;
}

bool set_batch(const llama_token *tokens,int count,int pos,bool show_logits) {
    llama_batch_ext_clear(batch);
    for(int i=0;i<count;++i) {
        int index=llama_batch_ext_add_token(batch,0,tokens[i]);
        if(index<0) return false;
        const llama_pos p=pos+i;
        if(!llama_batch_ext_set_pos(batch,index,&p)) return false;
        if(show_logits && i==count-1) {
            if(!llama_batch_ext_set_output_logits(batch,index,true)) return false;
        }
    }
    return true;
}

std::string run(const std::string& prompt,int requested_tokens) {
    if(!model||!context||!batch||!vocab) { phase.store(6); return ""; }
    cancel_requested.store(false);
    phase.store(1);
    prefill_done.store(0);
    prefill_total.store(0);
    decoded_tokens.store(0);
    const auto began=Clock::now();
    abort_deadline_ns.store(now_ns()+
         std::chrono::duration_cast<std::chrono::nanoseconds>(MAX_CPU_TIME).count());
    cpu_graph_stage.store(0);

    // Clear recurrent / KV memory between requests: no bleed across callers.
    llama_memory_t mem=llama_get_memory(context);
    if(mem) llama_memory_clear(mem,true);

    std::vector<llama_token> tokens(MAX_PROMPT+1);
    int n=llama_tokenize(vocab,prompt.c_str(),(int)prompt.size(),
                         tokens.data(),(int)tokens.size(),true,true);
    if(n<=0||n>MAX_PROMPT) { phase.store(6); return ""; }
    tokens.resize((size_t)n);
    prefill_total.store(n);
    phase.store(2);
    int cursor=0;
    // Smaller prefill chunks for cooperative cancellation and progress.
    for(int offset=0;offset<n;offset+=16) {
        if(terminated(began)) return "";
        int size=std::min(16,n-offset);
        cpu_graph_stage.store(size);
        if(!set_batch(tokens.data()+offset,size,offset,offset+size==n) ||
           llama_process(context,LLAMA_PROCESS_TYPE_DECODE,batch)!=0) {
            if(phase.load()!=4 && phase.load()!=5) phase.store(6);
            return "";
        }
        cursor+=size;
        prefill_done.store(cursor);
    }
    if(terminated(began)) return "";

    llama_sampler_chain_params sp=llama_sampler_chain_default_params();
    llama_sampler *sampler=llama_sampler_chain_init(sp);
    if(!sampler) { phase.store(6); return ""; }
    llama_sampler_chain_add(sampler,llama_sampler_init_top_k(24));
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    phase.store(3);
    const int max_output=std::min(MAX_OUTPUT,std::max(1,requested_tokens));
    for(int i=0;i<max_output;++i) {
        if(terminated(began)) { out.clear(); break; }
        const llama_token next=llama_sampler_sample(sampler,context,-1);
        if(llama_vocab_is_eog(vocab,next)) break;
        char piece[1024];
        int size=llama_token_to_piece(vocab,next,piece,sizeof(piece),0,true);
        if(size>0) {
            out.append(piece,(size_t)size);
            if(out.find("<|im_end|>")!=std::string::npos || out.size()>500) break;
        }
        decoded_tokens.store(i+1);
        if(cursor>=CONTEXT_TOKENS-1) break;
        if(!set_batch(&next,1,cursor,true) ||
           llama_process(context,LLAMA_PROCESS_TYPE_DECODE,batch)!=0) {
            if(phase.load()!=4 && phase.load()!=5) phase.store(6);
            out.clear();
            break;
        }
        ++cursor;
    }
    llama_sampler_free(sampler);
    if(phase.load()==3) phase.store(0);
    return out;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_in_textcall_lab_LocalModel_nativeLoad(JNIEnv *env,jclass,jstring path) {
    std::lock_guard<std::mutex> lock(gate);
    if(model && context && batch) return env->NewStringUTF("");
    if(!backend_initialized) {
        llama_backend_init();
        backend_initialized=true;
    }
    const char *chars=env->GetStringUTFChars(path,nullptr);
    if(!chars) return env->NewStringUTF("Model path unavailable");
    std::string filename(chars);
    env->ReleaseStringUTFChars(path,chars);
    llama_model_params mp=llama_model_default_params();
    mp.n_gpu_layers=0;
    mp.load_mode=LLAMA_LOAD_MODE_MMAP;
    model=llama_model_load_from_file(filename.c_str(),mp);
    if(!model) return env->NewStringUTF("Could not load GGUF. Check storage and supported architecture");
    vocab=llama_model_get_vocab(model);
    if(!vocab) return env->NewStringUTF("GGUF tokenizer missing");
    llama_context_params cp=llama_context_default_params();
    cp.n_ctx=CONTEXT_TOKENS;
    cp.n_batch=64;
    cp.n_ubatch=16;
    cp.abort_callback=abort_cpu_graph;
    cp.abort_callback_data=nullptr;
    cp.n_threads=4;
    cp.n_threads_batch=4;
    cp.no_perf=true;
    context=llama_init_from_model(model,cp);
    if(!context) return env->NewStringUTF("Not enough RAM to initialize AI context");
    batch=llama_batch_ext_init(context);
    if(!batch) return env->NewStringUTF("Unable to initialize token batch");
    phase.store(0);
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_in_textcall_lab_LocalModel_nativeGenerate(JNIEnv *env,jclass,jstring prompt,jint limit) {
    std::lock_guard<std::mutex> lock(gate);
    const char *s=env->GetStringUTFChars(prompt,nullptr);
    if(!s) return env->NewStringUTF("");
    std::string input(s);
    env->ReleaseStringUTFChars(prompt,s);
    std::string output=run(input,(int)limit);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_in_textcall_lab_LocalModel_nativeUnload(JNIEnv *,jclass) {
    cancel_requested.store(true);
    std::lock_guard<std::mutex> lock(gate);
    if(batch) { llama_batch_ext_free(batch); batch=nullptr; }
    if(context) { llama_free(context); context=nullptr; }
    if(model) { llama_model_free(model); model=nullptr; }
    vocab=nullptr;
    phase.store(0);
    abort_deadline_ns.store(0);
}

extern "C" JNIEXPORT void JNICALL
Java_in_textcall_lab_LocalModel_nativeCancel(JNIEnv *,jclass) {
    cancel_requested.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_in_textcall_lab_LocalModel_nativeProgress(JNIEnv *env,jclass) {
    std::string state;
    switch(phase.load()) {
        case 1: state="Tokenizing prompt"; break;
        case 2: state="Prefill (batch "+std::to_string(cpu_graph_stage.load())+
                       ") "+std::to_string(prefill_done.load())+"/"+
                       std::to_string(prefill_total.load())+" tokens"; break;
        case 3: state="Generating "+std::to_string(decoded_tokens.load())+" reply tokens"; break;
        case 4: state="CPU budget exceeded (12s)"; break;
        case 5: state="Inference cancelled"; break;
        case 6: state="Inference failed (see model/support)"; break;
        default: state="Model idle"; break;
    }
    return env->NewStringUTF(state.c_str());
}

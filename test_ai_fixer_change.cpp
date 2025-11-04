// Test file for AI Fixer Bot - intentionally contains issues
// This file will be deleted after testing

#include <jni.h>
#include <string.h>

// Issue 1: Missing ReleaseByteArrayElements
jbyteArray test_missing_release(JNIEnv* env, jbyteArray data) {
    jbyte* ptr = env->GetByteArrayElements(data, NULL);
    // Missing: env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
    return data;
}

// Issue 2: Format specifier mismatch
void test_format_mismatch(int value) {
    printf("%d", (size_t)value);  // Should be %zu for size_t
}

// Issue 3: Unused parameter
jint test_unused_param(JNIEnv* env, jobject thiz, jint unused) {
    // Should mark with (void)unused;
    return 0;
}

// Issue 4: Potential null dereference
void test_null_check(char* ptr) {
    int len = strlen(ptr);  // Missing null check
}


#include <jni.h>

#include "autocam/add.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_autocam_core_NativeCore_add(JNIEnv* /*env*/, jclass /*clazz*/, jint a, jint b) {
    return autocam::add(static_cast<int>(a), static_cast<int>(b));
}

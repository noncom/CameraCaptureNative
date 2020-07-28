#include "RenderingPlugin.h"

#include <GLES2/gl2.h>
#include <jni.h>
#include <android/log.h>

#define LOG_TAG "RENDERING_PLUGIN"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void *g_TexturePointer = NULL;
static JavaVM *gJavaVM;
static jobject gCallbackObject = NULL;

extern "C" void UNITY_INTERFACE_EXPORT UNITY_INTERFACE_API SetTextureFromUnity(void *texturePtr)
{
	// A script calls this at initialization time; just remember the texture pointer here.
	// Will update texture pixels each frame from the plugin rendering event (texture update
	// needs to happen on the rendering thread).
	g_TexturePointer = texturePtr;

	LOGD("########################## SetTextureFromUnity texturePtr=%p\n", g_TexturePointer);
}

static void UNITY_INTERFACE_API OnRenderEvent(int eventID)
{
	if (g_TexturePointer)
	{
        //LOGD("########################## ON RENDER EVENT A gCallbackObject=%p, env=%p\n", gCallbackObject, gJavaVM);
		int status;
		JNIEnv *env;
		int isAttached = 0;

		if (!gCallbackObject) {
            //LOGD("########################## ON RENDER EVENT A-1 returning!");
            return;
        }

        //LOGD("########################## ON RENDER EVENT B texturePtr=%p\n", g_TexturePointer);

		if ((status = gJavaVM->GetEnv((void **)&env, JNI_VERSION_1_6)) < 0)
		{
			if ((status = gJavaVM->AttachCurrentThread(&env, NULL)) < 0)
			{
				return;
			}
			isAttached = 1;
		}

        //LOGD("########################## ON RENDER EVENT C gCallbackObject=%p\n", gCallbackObject);

		jclass cls = env->GetObjectClass(gCallbackObject);
		if (!cls)
		{
			if (isAttached)
				gJavaVM->DetachCurrentThread();
			return;
		}

        //LOGD("########################## ON RENDER EVENT D texturePtr=%p\n", g_TexturePointer);

		jmethodID method = env->GetMethodID(cls, "requestJavaRendering", "(I)V");
		if (!method)
		{
			if (isAttached)
				gJavaVM->DetachCurrentThread();
			return;
		}

        //LOGD("########################## ON RENDER EVENT E texturePtr=%p\n", g_TexturePointer);

		GLuint gltex = (GLuint)(size_t)(g_TexturePointer);
		env->CallVoidMethod(gCallbackObject, method, (int)gltex);

        //LOGD("########################## ON RENDER EVENT F texturePtr=%p\n", g_TexturePointer);

		if (isAttached)
			gJavaVM->DetachCurrentThread();

        //LOGD("########################## ON RENDER EVENT G texturePtr=%p\n", g_TexturePointer);
	}
}

// --------------------------------------------------------------------------
// GetRenderEventFunc, used to get a rendering event callback function.
extern "C" UnityRenderingEvent UNITY_INTERFACE_EXPORT UNITY_INTERFACE_API GetRenderEventFunc()
{
	return OnRenderEvent;
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	gJavaVM = vm;

	return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_cameracapturenative_CameraPluginActivity_nativeInit(JNIEnv *env, jobject obj)
{
	//LOGD("########################## NATIVE INIT");
	gCallbackObject = env->NewGlobalRef(obj);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_cameracapturenative_CameraPluginActivity_nativeRelease(JNIEnv *env, jobject obj)
{
    //LOGD("########################## NATIVE RELEASE");
	env->DeleteGlobalRef(gCallbackObject);
	gCallbackObject = NULL;
}
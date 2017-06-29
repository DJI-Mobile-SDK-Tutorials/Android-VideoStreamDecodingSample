# Android.mk for FFmpeg
#
# Lei Xiaohua ������
# leixiaohua1020@126.com
# http://blog.csdn.net/leixiaohua1020
# 

LOCAL_PATH := $(call my-dir)

# FFmpeg library
include $(CLEAR_VARS)
LOCAL_MODULE := ffmpegjni
LOCAL_SRC_FILES := libffmpegjni.so
include $(PREBUILT_SHARED_LIBRARY)



# Program
include $(CLEAR_VARS)
PATH_TO_LIBFFMPEG_SO=$(LOCAL_PATH)
LOCAL_MODULE := djivideojni
LOCAL_SRC_FILES :=dji_video_jni.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDLIBS := -llog -lz
LOCAL_SHARED_LIBRARIES := ffmpegjni
include $(BUILD_SHARED_LIBRARY)

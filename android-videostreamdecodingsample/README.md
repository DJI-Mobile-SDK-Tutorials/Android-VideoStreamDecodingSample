# Android Video Stream Decoding Sample

## Introduction

This sample project demonstrates how to use [FFmpeg](https://ffmpeg.org) for video frame parsing and to use the `MediaCodec` for hardware decoding. It will help to parse video frames and decode the raw video stream data from DJI Camera and output the [YUV](https://en.wikipedia.org/wiki/YUV) data. 

## Java Files Explanation

When you open the project in Android Studio, you may see that there are four java files in the "com.dji.livestreamtranscoder" package, please check the followings for more detials:

### DJIVideoStreamDecoder.java

   This class is a helper class for hardware decoding, it manages tasks including raw data frame parsing, inserting i-frame, using `MediaCodec` for decoding, etc. 
   
   Here are the steps for you to learn how to use this class:
   
1. Initialize and set the instance as a listener of NativeDataListener to receive the frame data.

2. Send the raw data from camera to ffmpeg for frame parsing.
 
3. Get the parsed frame data from ffmpeg parsing frame callback and cache the parsed framed data into the frameQueue.
 
4. Initialize the MediaCodec as a decoder and then check whether there is any i-frame in the MediaCodec. If not, get the default i-frame from sdk resource and insert it at the head of frameQueue. Then dequeue the framed data from the frameQueue and feed it(which is Byte buffer) into the MediaCodec.

5. Get the output byte buffer from MediaCodec, if a surface(Video Previewing View) is configured in the MediaCodec, the output byte buffer is only need to be released. If not, the output yuv data should invoke the callback and pass it out to external listener, it should also be released too.

6. Release the ffmpeg and the MediaCodec, stop the decoding thread.

### NativeHelper.java

  This is a helper class to invoke native methods. Since we need invoke FFmpeg functions on JNI layer, this class is defined as an assistant to invoke all JNI methods and receive callback data.

### VideoDecodingApplication.java

  It's an Application class to do DJI SDK Registration, product connection, product change and product connectivity change checking. Then use broadcast to send the changes.

### MainActivity.java

  It's an Activity class to implement the features of the sample project, like implementing the UI elements, init a SurfaceView to preview the live stream data, save buffer data into a JPEG image file, etc.
  
For more details, please check the sample project's source code.
  
  
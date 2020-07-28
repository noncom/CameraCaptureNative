# Camera Capture Native for Unity Android

This is an Android-native plugin for Unity to access the device cameras.
Unity has a far more comfortable way of doing this with `WebCamTexture`
and in most cases you should prefer that. However, sometimes there might
be a need to access that process on a lower level either with the Android
API or the native C++ code, for various reasons. This project provides
these two points of access.

# Inspiration

This project is heavily based on https://github.com/robsondepaula/unity-android-native-camera

Key differences include:
- The Android API and Android Native parts are joined into one single project now. This
has implications when building, refer to the **Building the Project** section for more info.
- Native C++ part is moved to *CMake 3.10.2* from *ndk-build*
- The Java part of the project is rewritten in Kotlin. This again has build implications.
- There is no release/debug choice in gradle.build now, please refer to the original project
if you want that.

# Making it work

Two main things are required to make it work: building the project itself and interfacing it
with Unity.

## Building the Project (important!)

Simply run the build from Android Studio and collect the *aar* artifact in the output.

### Notice on Kotlin

Since the project relies on Kotlin, it introduces the additional dependency of kotlin stdlib that's necessary to make it work. To include it into the Unity project:

- In Android Studio expand "Additional Dependencies" and locate `org.jetbrains.kotlin:kotlin-stdlib:<version>@jar`
- Expand it and right-click on the library root, then choose "Show in Explorer"
- Copy the shown *jar* into `Assets/Plugins/Android`

This additional *jar* is always required in your Unity project to make Kotlin work.

### Notice on native libraries

By default the native libraries are compiled into the *aar* as well, so you don't have
to separately put anything else into the Unity project except the *aar* itself. Put the
*aar* artifact into `Assets/Plugins/Android` and it should be ready to use.

However if you want to reduce the *aar* size and/or have more control over the native
libraries, then:
- Make a build and collect the built native libraries in
`build/intermediates/stripped_native_libs/debug/out/lib`. For example you might only
want to copy the `arm*` variations and leave out the `x86*` ones. Put the native
libraries into the `Assets/Plugins/Android/Libs` folder, where each lib goes to its
corresponding folder, for example `Assets/Plugins/Android/Libs/arm64-v8a`, just as they
are arranged by the build.
- Comment out the `Native C++ part building` section of the `app/build.gradle` and rebuild
the project. Now the resulting *aar* won't have the native libraries included and should
be significantly less in size. Copy it to `Assets/Plugins/Android` just like before.

**Important:** if you change the Java/Kotlin packaging of this library, remember to change
the corresponding function names in `RenderingPlugin.cpp`.

## The Unity Part

The additional setup is required on Unity part:

- The complementing Unity script for interacting with this library is available in the
`Unity` folder of this repo. Copy it to anywhere in your Unity project.

- Copy the `app/src/main/AndroidManifest.xml` file to `Assets/Plugins/Android` to make Unity
properly recognize the library

**Important:** if you change the Java/Kotlin packaging of this library, remember to change
the corresponding strings in `CameraPluginWrapper.cs` and `AndroidManifest.xml`.


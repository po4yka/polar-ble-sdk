# About iOS Communications

The iOS Communications library provides three functionalities for communication with Polar devices and sensors over Bluetooth LE:
 
* **Functionality 1 - iOS Communications:** This is the base functionality of the communication library, which provides connection and communication with Polar devices and sensors over Bluetooth LE. It is used by many applications developed by Polar. The source code for this functionality can be found in `ios-communications/Sources/iOSCommunications/`.
* **Functionality 2 - Polar BLE SDK:** This functionality provides connection and communication with Polar devices and sensors over Bluetooth LE for 3rd party developers. The functionality is achieved by wrapping the base iOS Communications library with an SDK layer. The source code for this functionality is located in the `ios-communications/Sources/PolarBleSdk/`.
* **Functionality 3 - Polar BLE SDK PROPRIETARY:** The proprietary SDK is intended for internal development at Polar. It is separated into its own branch,  `sdk-proprietary`.

iOS Communications XCode project (i.e. `iOSCommunications.xcodeproj`) contains three targets `iOSCommunications`, `PolarBleSdk` and `PolarBleSdkWatchOs`. `iOSCommunications` target implements the `Functionality 1`. The targets `PolarBleSdk` and `PolarBleSdkWatchOs` implements the `Functionality 2`, both targets dependents on `iOSCommunications` target.

* [Environment Requirements](#environment-requirements)
* [Dependencies](#dependencies)
    * [To update dependencies](#to-update-dependencies)
* [Install](#install)
    * [... using Swift Package Manager](#...-using-Swift-Package-Manager)
    * [... using XCFramework](#...-using-XCFramework)
    * [... using git submodules](#...-using-git-submodules)
* [Usage](#usage)
* [Debugging](#debugging)
* [Project maintenance](XcodeProjectWorkflow.md)

## Environment Requirements

* Xcode 15.0 +
* Swift 5.9 +

## Dependencies
* iOS Communications project is dependent on following libraries
   * [SwiftProtobuf](https://github.com/apple/swift-protobuf). `SwiftProtobuf` dependency is only required by the targets `PolarBleSdk` and `PolarBleSdkWatchOs`
   * [ZIPFoundation](https://github.com/weichsel/ZIPFoundation). `ZIPFoundation` dependency is required by the iOS `iOSCommunications` and `PolarBleSdk` targets and the Swift package product.
   
### To update dependencies
 * the dependent libraries are referenced by Swift Package Manager. To update dependencies, modify `Package.swift` and the Xcode Swift package references.
 
## Install 

Only Swift Package Manager, XCFramework, and direct Xcode project integration are supported.

### ... using Swift Package Manager 
* iOS Communications project is made available via Swift Package. Please get familiar with [XCode help](https://developer.apple.com/documentation/swift_packages/adding_package_dependencies_to_your_app) how to take Swift Package into use on your project.

### ... using XCFramework
* From `/scripts` folder run the `./build_ios_communications.sh` or  `./build_sdk.sh` depending on target of your choice. Both scripts builds the XCFrameworks. 
* Copy generated XCFramework from `iOSCommunicationsBuild/`  or from `SdkBuild/` to your project

### ... using git submodules
* `git submodule add <repository_url>`
* Drag and drop the iOS communications project file (i.e. `iOSCommunications.xcodeproj`) to yours Xcode project Project Navigator
*  In your project choose the `Target → General → Frameworks, Libraries and Embedded Content → Press "+"`. From opened dialog select the target `iOSCommunications`, `PolarBleSdk` or `PolarBleSdkWatchOs` depending on your needs.  

## Usage
* **Swift Concurrency and Combine**
    * Use the async APIs directly and add `import Combine` only where your app consumes `AnyPublisher` streams.
* **Permissions**
    * In project target settings enable __Background Modes__, add  __Uses Bluetooth LE accessories__
    * In your project target property list add the key  [NSBluetoothAlwaysUsageDescription](https://developer.apple.com/documentation/bundleresources/information_property_list/nsbluetoothalwaysusagedescription)
    ```xml
    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>Needs BLE permission</string>
    ```

## Debugging

To debug the iOS communications or Polar BLE SDK target follow the steps provided below:

1. Clone the iOS communications repository (this repository) by running `git clone <repository_url>`.
2. Follow the steps below depending on which dependency your app uses.

### If you are using Swift Package Manager as a dependency in your app

1. Open your app’s Xcode project.

2. Select the Swift package’s folder (i.e. `/sources/iOS/ios-communications/`) in Finder and drag it into the Project navigator. This action adds your dependency’s Swift package as a local package to your project.

Swift Package Manager and watchOS builds can link shared KMP through `PolarBleSdkShared.xcframework` when a release binary target URL/checksum or local generated XCFramework is available. Clean checkouts continue to use the Swift fallback implementation guarded by `#if canImport(PolarBleSdkShared)`.

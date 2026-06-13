// swift-tools-version:5.5
import Foundation
import PackageDescription

let polarBleSdkSharedXCFrameworkPath = "sources/iOS/ios-communications/Generated/PolarBleSdkSharedXCFramework/PolarBleSdkShared.xcframework"
let polarBleSdkSharedBinaryURL = ProcessInfo.processInfo.environment["POLAR_BLE_SDK_SHARED_BINARY_URL"]
let polarBleSdkSharedBinaryChecksum = ProcessInfo.processInfo.environment["POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM"]
let hasRemotePolarBleSdkSharedBinary = polarBleSdkSharedBinaryURL?.isEmpty == false && polarBleSdkSharedBinaryChecksum?.isEmpty == false
let hasLocalPolarBleSdkSharedXCFramework = FileManager.default.fileExists(atPath: polarBleSdkSharedXCFrameworkPath)
let hasPolarBleSdkShared = hasRemotePolarBleSdkSharedBinary || hasLocalPolarBleSdkSharedXCFramework
let polarBleSdkTargetDependencies: [Target.Dependency] = [
    "SwiftProtobuf",
    "Zip"
] + (hasPolarBleSdkShared ? [.target(name: "PolarBleSdkShared")] : [])
let polarBleSdkSharedTargets: [Target] = hasRemotePolarBleSdkSharedBinary ? [
    .binaryTarget(
        name: "PolarBleSdkShared",
        url: polarBleSdkSharedBinaryURL!,
        checksum: polarBleSdkSharedBinaryChecksum!
    )
] : (hasLocalPolarBleSdkSharedXCFramework ? [
    .binaryTarget(
        name: "PolarBleSdkShared",
        path: polarBleSdkSharedXCFrameworkPath
    )
] : [])
let polarBleSdkTargets: [Target] = polarBleSdkSharedTargets + [
    .target(
        name: "PolarBleSdk",
        dependencies: polarBleSdkTargetDependencies,
        path: "sources/iOS/ios-communications/Sources",
        exclude: ["iOSCommunications/Info.plist", "PolarBleSdk/Info.plist"],
        resources: [.process("iOSCommunications/Resources")]
    ),
]

let package = Package(
    name: "PolarBleSdk",
    platforms: [
        .iOS(.v14), .watchOS(.v5)
    ],
    products: [
        .library( name: "PolarBleSdk", targets: ["PolarBleSdk"]),
    ],
    dependencies: [
        .package(name: "SwiftProtobuf", url: "https://github.com/apple/swift-protobuf.git", from: "1.6.0"),
	    .package(url: "https://github.com/marmelroy/Zip.git", from: "2.1.2"),
    ],
    targets: polarBleSdkTargets
)

// swift-tools-version:5.5
import Foundation
import PackageDescription

let polarBleSdkSharedXCFrameworkPath = "sources/iOS/ios-communications/Generated/PolarBleSdkSharedXCFramework/PolarBleSdkShared.xcframework"
let hasPolarBleSdkSharedXCFramework = FileManager.default.fileExists(atPath: polarBleSdkSharedXCFrameworkPath)
let polarBleSdkTargetDependencies: [Target.Dependency] = [
    "SwiftProtobuf",
    "Zip"
] + (hasPolarBleSdkSharedXCFramework ? [.target(name: "PolarBleSdkShared")] : [])
let polarBleSdkTargets: [Target] = (hasPolarBleSdkSharedXCFramework ? [
    .binaryTarget(
        name: "PolarBleSdkShared",
        path: polarBleSdkSharedXCFrameworkPath
    )
] : []) + [
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

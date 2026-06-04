import Foundation

private final class GoldenVectorBundleMarker {}

enum GoldenVectorTestData {
    static func repositoryRoot(filePath: String = #filePath) throws -> URL {
        if let bundledRoot = bundledRepositoryRoot() {
            return bundledRoot
        }

        var candidates = [
            ProcessInfo.processInfo.environment["POLAR_BLE_SDK_REPOSITORY_ROOT"],
            ProcessInfo.processInfo.environment["SRCROOT"],
            ProcessInfo.processInfo.environment["PROJECT_DIR"],
            ProcessInfo.processInfo.environment["SOURCE_ROOT"],
            ProcessInfo.processInfo.environment["PWD"],
            FileManager.default.currentDirectoryPath,
            filePath
        ].compactMap { value -> URL? in
            guard let value, !value.isEmpty else { return nil }
            return URL(fileURLWithPath: value)
        }

        candidates.append(contentsOf: Bundle.allBundles.map(\.bundleURL))
        candidates.append(contentsOf: Bundle.allFrameworks.map(\.bundleURL))

        for candidate in candidates {
            if let root = repositoryRoot(startingAt: candidate) {
                return root
            }
        }

        let searched = candidates.map { $0.path }.joined(separator: ", ")
        throw NSError(domain: "GoldenVectorTestData", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not find repository root with testdata/golden-vectors; searched \(searched)"])
    }

    private static func bundledRepositoryRoot() -> URL? {
        let bundles = [
            Bundle(for: GoldenVectorBundleMarker.self),
            Bundle.main
        ] + Bundle.allBundles

        for bundle in bundles {
            if let goldenVectors = bundle.url(forResource: "golden-vectors", withExtension: nil, subdirectory: "testdata") {
                return goldenVectors.deletingLastPathComponent().deletingLastPathComponent()
            }
        }
        return nil
    }

    private static func repositoryRoot(startingAt url: URL) -> URL? {
        var directory = normalizedDirectory(for: url)
        while true {
            let candidate = directory.appendingPathComponent("testdata/golden-vectors")
            if FileManager.default.fileExists(atPath: candidate.path) {
                return directory
            }
            let parent = directory.deletingLastPathComponent()
            if parent.path == directory.path {
                return nil
            }
            directory = parent
        }
    }

    private static func normalizedDirectory(for url: URL) -> URL {
        var candidate = url
        if candidate.pathExtension == "swift" || candidate.pathExtension == "json" || candidate.pathExtension == "plist" {
            candidate.deleteLastPathComponent()
        }
        return candidate
    }

    static func root() throws -> URL {
        try repositoryRoot().appendingPathComponent("testdata/golden-vectors")
    }

    static func directory(_ relativePath: String) throws -> URL {
        try root().appendingPathComponent(relativePath)
    }

    static func loadObject(_ relativePath: String) throws -> [String: Any] {
        let data = try Data(contentsOf: root().appendingPathComponent(relativePath))
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "GoldenVectorTestData", code: 2, userInfo: [NSLocalizedDescriptionKey: "\(relativePath) is not a JSON object"])
        }
        return object
    }

    static func loadObjects(in relativeDirectory: String) throws -> [[String: Any]] {
        let directory = try self.directory(relativeDirectory)
        return try FileManager.default
            .contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { url in
                let data = try Data(contentsOf: url)
                guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw NSError(domain: "GoldenVectorTestData", code: 3, userInfo: [NSLocalizedDescriptionKey: "\(url.lastPathComponent) is not a JSON object"])
                }
                return object
            }
    }
}

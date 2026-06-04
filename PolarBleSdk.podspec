Pod::Spec.new do |s|  
    s.name              = 'PolarBleSdk'
    s.version		 = '8.0.0'
    s.summary           = 'SDK for Polar sensors'
    s.homepage          = 'https://github.com/polarofficial/polar-ble-sdk'
    s.license           = { :type => 'Custom', :file => 'Polar_SDK_License.txt' }
    s.authors           = 'Polar Electro Oy'  
    s.swift_versions    = '5.0'
    s.cocoapods_version = '>= 1.10'
    s.source            = { :git => 'https://github.com/polarofficial/polar-ble-sdk.git', :tag => s.version.to_s }

    s.ios.deployment_target = '14.0'
    
    s.source_files = 'sources/iOS/ios-communications/Sources/**/*.{swift,h}'
    s.exclude_files = '**/Tests/**/*', '**/*Tests*'
    s.resources		 = ['sources/iOS/ios-communications/Sources/iOSCommunications/Resources/polar_device_capabilities.json']
    s.preserve_paths = 'sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh'
    s.pod_target_xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
        'FRAMEWORK_SEARCH_PATHS' => '$(inherited) $(PODS_TARGET_SRCROOT)/sources/iOS/ios-communications/Generated/PolarBleSdkShared/$(PLATFORM_NAME)',
        'OTHER_LDFLAGS' => '$(inherited) -framework PolarBleSdkShared'
    }
    s.script_phase = {
        :name => 'Build PolarBleSdkShared KMP Framework',
        :script => '${PODS_TARGET_SRCROOT}/sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh',
        :execution_position => :before_compile
    }
    s.dependency 'SwiftProtobuf', '~> 1.0'
    s.dependency 'Zip', '~> 2.1.2'
end

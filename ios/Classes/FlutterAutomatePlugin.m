#import "FlutterAutomatePlugin.h"
#if __has_include(<flutter_automate/flutter_automate-Swift.h>)
#import <flutter_automate/flutter_automate-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_automate-Swift.h"
#endif

@implementation FlutterAutomatePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterAutomatePlugin registerWithRegistrar:registrar];
}
@end

require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))
name = package["name"]
homepage = package["homepage"] || "https://www.npmjs.com/package/#{name}"
repo_url = package["repository"].is_a?(Hash) ? package["repository"]["url"] : nil
# Use a real git URL in package.json before tagging releases; until then CocoaPods resolves from node_modules via :path.
use_git = repo_url && !repo_url.include?("YOUR_GITHUB_USER")
git_clean = repo_url&.sub(/^git\+/, "")&.sub(/\.git\z/, "")

Pod::Spec.new do |s|
  s.name         = name
  s.version      = package["version"]
  s.summary      = package["description"].split(".").first
  s.homepage     = homepage
  s.license      = package["license"] || "MIT"
  s.author       = package["author"].to_s.empty? ? "Contributors" : package["author"]
  s.platforms    = { :ios => "13.0" }
  s.source       =
    if use_git && git_clean
      { :git => "#{git_clean}.git", :tag => "v#{s.version}" }
    else
      { :path => "." }
    end

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.requires_arc = true

  s.dependency "React-Core"

  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
    "SWIFT_COMPILATION_MODE" => "wholemodule",
    "CLANG_ALLOW_NON_MODULAR_INCLUDES_IN_FRAMEWORK_MODULES" => "YES",
  }
end

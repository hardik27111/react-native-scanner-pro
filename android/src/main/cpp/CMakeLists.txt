cmake_minimum_required(VERSION 3.18.1)
project(scannerpro_vision)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Optimization flags
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG -ffast-math -fvisibility=hidden")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O0 -g -DDEBUG")

# Warning flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Werror=return-type")

# Vision math library
add_library(
  scannerpro_vision
  SHARED
  VisionMathCore.cpp
  VisionMathBridge.cpp
)

# Find and link required libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
  scannerpro_vision
  ${log-lib}
  ${android-lib}
)

# Include directories
target_include_directories(
  scannerpro_vision
  PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}
)

# Export all symbols for JNI
set_target_properties(
  scannerpro_vision
  PROPERTIES
  CXX_VISIBILITY_PRESET default
)

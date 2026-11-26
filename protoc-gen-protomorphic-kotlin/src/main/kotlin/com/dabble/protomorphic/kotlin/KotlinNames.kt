package com.dabble.protomorphic.kotlin

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.EnumDescriptor
import com.google.protobuf.Descriptors.FileDescriptor

/** The Kotlin package of [file]'s declarations: its java_package, or else its proto package. */
fun kotlinPackage(file: FileDescriptor): String = file.options.javaPackage.ifEmpty { file.`package` }

/** Fully-qualified Kotlin name of a message, e.g. `com.example.Outer.Inner`. */
fun kotlinName(descriptor: Descriptor): String =
    kotlinPackage(descriptor.file) + "." + nesting(descriptor.containingType) + descriptor.name

/** Fully-qualified Kotlin name of an enum. */
fun kotlinName(descriptor: EnumDescriptor): String =
    kotlinPackage(descriptor.file) + "." + nesting(descriptor.containingType) + descriptor.name

private fun nesting(parent: Descriptor?): String =
    if (parent == null) "" else nesting(parent.containingType) + parent.name + "."

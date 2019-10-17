package com.jakewharton.diffuse

import com.android.dex.ClassDef
import com.android.dex.FieldId
import com.android.dex.MethodId
import com.jakewharton.diffuse.io.Input
import okio.BufferedSource
import com.android.dex.Dex as AndroidDex

class Dex private constructor(
  val strings: List<String>,
  val types: List<String>,
  val classes: List<TypeDescriptor>,
  val declaredMembers: List<Member>,
  val referencedMembers: List<Member>
) {
  val members = declaredMembers + referencedMembers

  fun withMapping(mapping: ApiMapping): Dex {
    if (mapping.isEmpty()) return this

    // TODO map types
    val mappedClasses = classes.map(mapping::get)
    val mappedDeclaredMembers = declaredMembers.map(mapping::get)
    val mappedReferencedMembers = referencedMembers.map(mapping::get)
    return Dex(strings, types, mappedClasses, mappedDeclaredMembers, mappedReferencedMembers)
  }

  companion object {
    @JvmStatic
    @JvmName("parse")
    fun Input.toDex(): Dex {
      val bytes = source().use(BufferedSource::readByteArray)
      val dex = AndroidDex(bytes)
      val classes = dex.classDefs()
          .map { TypeDescriptor(dex.typeNames()[it.typeIndex]) }
      val declaredTypeIndices = dex.classDefs()
          .map(ClassDef::getTypeIndex)
          .toSet()
      val (declaredMethods, referencedMethods) = dex.methodIds()
          .partition { it.declaringClassIndex in declaredTypeIndices }
          .mapEach { it.map(dex::getMethod) }
      val (declaredFields, referencedFields) = dex.fieldIds()
          .partition { it.declaringClassIndex in declaredTypeIndices }
          .mapEach { it.map(dex::getField) }
      val declaredMembers = declaredMethods + declaredFields
      val referencedMembers = referencedMethods + referencedFields
      return Dex(dex.strings(), dex.typeNames(), classes, declaredMembers, referencedMembers)
    }
  }
}

private fun AndroidDex.getMethod(methodId: MethodId): Method {
  val declaringType = TypeDescriptor(typeNames()[methodId.declaringClassIndex])
  val name = strings()[methodId.nameIndex]
  val methodProtoIds = protoIds()[methodId.protoIndex]
  val parameterTypes = readTypeList(methodProtoIds.parametersOffset).types
      .map { TypeDescriptor(typeNames()[it.toInt()]) }
  val returnType = TypeDescriptor(typeNames()[methodProtoIds.returnTypeIndex])
  return Method(declaringType, name, parameterTypes, returnType)
}

private fun AndroidDex.getField(fieldId: FieldId): Field {
  val declaringType = TypeDescriptor(typeNames()[fieldId.declaringClassIndex])
  val name = strings()[fieldId.nameIndex]
  val type = TypeDescriptor(typeNames()[fieldId.typeIndex])
  return Field(declaringType, name, type)
}

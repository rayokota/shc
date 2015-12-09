/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.spark.sql

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.spark.sql.AvroHBaseRecord
import org.apache.spark.sql.execution.datasources.hbase.{AvroSedes, SchemaConverters}
import org.apache.spark.{SparkContext, Logging}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite}

import scala.util.Random

class AvroRecordSuite extends FunSuite with BeforeAndAfterEach with BeforeAndAfterAll  with Logging {



  test("avro to schema converterBasic setup") {
    val schemaString  =
      s"""{"namespace": "example.avro",
         |   "type": "record",      "name": "User",
         |    "fields": [      {"name": "name", "type": "string"},
         |      {"name": "favorite_number",  "type": ["int", "null"]},
         |        {"name": "favorite_color", "type": ["string", "null"]}      ]    }""".stripMargin
    val avroSchema: Schema = {
      val p = new Schema.Parser
      p.parse(schemaString)
    }

    val user1 = new GenericData.Record(avroSchema);
    user1.put("name", "Alyssa");
    user1.put("favorite_number", 256);
    // Leave favorite color null

    val user2 = new GenericData.Record(avroSchema);
    user2.put("name", "Ben");
    user2.put("favorite_number", 7);
    user2.put("favorite_color", "red");

    val sqlUser1 = SchemaConverters.createConverterToSQL(avroSchema)(user1)
    println(sqlUser1)
    val schema = SchemaConverters.toSqlType(avroSchema)
    println(s"\nSqlschema: $schema")
    val avroUser1 = SchemaConverters.createConverterToAvro(schema.dataType, "avro", "example.avro")(sqlUser1)
    val avroByte = AvroSedes.serialize(avroUser1, avroSchema)
    val avroUser11 = AvroSedes.deserialize(avroByte, avroSchema)
    println(s"$avroUser1")
  }

  test("test schema complicated") {
    val schemaString =
      s"""{
             "type" : "record",
             "name" : "test_schema",
             "fields" : [{
               "name" : "string",
               "type" : "string",
               "doc"  : "Meaningless string of characters"
             }, {
               "name" : "simple_map",
               "type" : {"type": "map", "values": "int"}
             }, {
               "name" : "complex_map",
               "type" : {"type": "map", "values": {"type": "map", "values": "string"}}
             }, {
               "name" : "union_string_null",
               "type" : ["null", "string"]
             }, {
               "name" : "union_int_long_null",
               "type" : ["int", "long", "null"]
             }, {
               "name" : "union_float_double",
               "type" : ["float", "double"]
             }, {
               "name": "fixed3",
               "type": {"type": "fixed", "size": 3, "name": "fixed3"}
             }, {
               "name": "fixed2",
               "type": {"type": "fixed", "size": 2, "name": "fixed2"}
             }, {
               "name": "enum",
               "type": { "type": "enum",
                         "name": "Suit",
                         "symbols" : ["SPADES", "HEARTS", "DIAMONDS", "CLUBS"]
                       }
             }, {
               "name": "record",
               "type": {
                 "type": "record",
                 "name": "record",
                 "aliases": ["RecordAlias"],
                 "fields" : [{
                   "name": "value_field",
                   "type": "string"
                 }]
               }
             }, {
               "name": "array_of_boolean",
               "type": {"type": "array", "items": "boolean"}
             }, {
               "name": "bytes",
               "type": "bytes"
             }]
           }""".stripMargin
    val avroSchema: Schema = {
      val p = new Schema.Parser
      p.parse(schemaString)
    }
    val schema = SchemaConverters.toSqlType(avroSchema)
    println(s"\nSqlschema: $schema")
  }

  test("complicated") {
    val schemaComplex =
      s"""{"type" : "record",
        |  "name" : "test_schema",
        |    "fields" : [{
        |        "name" : "string",
        |        "type" : "string",
        |        "doc"  : "Meaningless string of characters"
        |      }, {
        |        "name" : "simple_map",
        |        "type" : {"type": "map", "values": "int"}
        |      }, {
        |        "name" : "union_int_long_null",
        |        "type" : ["int", "long", "null"]
        |      }, {
        |        "name" : "union_float_double",
        |        "type" : ["float", "double"]
        |      }, {
        |        "name": "inner_record",
        |        "type": {
        |           "type": "record",
        |           "name": "inner_record",
        |           "aliases": ["RecordAlias"],
        |           "fields" : [{
        |               "name": "value_field",
        |                "type": "string"
        |            }]
        |         }
        |      }, {
        |        "name": "array_of_boolean",
        |        "type": {"type": "array", "items": "boolean"}
        |      }, {
        |        "name": "bytes",
        |        "type": "bytes"
        |      }]
        |  }""".stripMargin

    val avroComplex: Schema = {
      val p = new Schema.Parser
      p.parse(schemaComplex)
    }
    val objectSize = 10 // Maps, arrays and strings in our generated file have this size
    val schema = SchemaConverters.toSqlType(avroComplex)
    println(s"\nSqlschema: $schema")
    // Create data that we will put into the avro file
    val avroRec = new GenericData.Record(avroComplex)
    val innerRec = new GenericData.Record(avroComplex.getField("inner_record").schema())
    innerRec.put("value_field", "Inner string")
    val rand = new Random()

    avroRec.put("string", rand.nextString(objectSize))
    avroRec.put("simple_map", TestUtils.generateRandomMap(rand, objectSize))
    avroRec.put("union_int_long_null", rand.nextInt())
    avroRec.put("union_float_double", rand.nextDouble())
    avroRec.put("inner_record", innerRec)
    avroRec.put("array_of_boolean", TestUtils.generateRandomArray(rand, objectSize))
    avroRec.put("bytes", TestUtils.generateRandomByteBuffer(rand, objectSize))
    println(s"\navroRec: $avroRec")
    val sqlRec = SchemaConverters.createConverterToSQL(avroComplex)(avroRec)
    println(s"\nsqlRec: $sqlRec")

    val avroRec1 = SchemaConverters.createConverterToAvro(schema.dataType, "test_schema", "example.avro")(sqlRec)
    println(s"\navroRec1: $avroRec1")
    val avroByte = AvroSedes.serialize(avroRec1, avroComplex)
    println("\nserialize")
    val avroRec11 = AvroSedes.deserialize(avroByte, avroComplex)
    println(s"\navroRec11: $avroRec11")
    val sqlRec1 = SchemaConverters.createConverterToSQL(avroComplex)(avroRec11)
    println(s"sqlRec1: $sqlRec1")
  }
}

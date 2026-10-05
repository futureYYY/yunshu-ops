package com.rackexcel.mobile.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RackValidatorTest {
    @Test
    fun parses_only_explicitly_visible_export_fields() {
        val rack = RackJsonParser.parse(
            """
            {
              "cabinet_id":"K10",
              "rack_fields":{"physical_location":"A区-03","cabinet_power":"6kW","power_type":"双路市电","temperature":"22±2℃"},
              "devices":[{
                "type":"服务器","bottom_u":21,"height_u":2,"confidence":0.93,
                "evidence":"右侧轨 U21-U22","display_name":"服务器",
                "model":"SRV-A1","manufacturer":"厂商B","power_w":500
              }],
              "uncertain":[]
            }
            """.trimIndent(),
            "K10.jpg",
        )

        assertEquals("A区-03", rack.metadata.physicalLocation)
        assertEquals("6kW", rack.metadata.cabinetPower)
        assertEquals("双路市电", rack.metadata.powerType)
        assertEquals("22±2℃", rack.metadata.temperature)
        assertEquals("RH2288H V6", rack.devices.single().model)
        assertEquals("超聚变", rack.devices.single().manufacturer)
        assertEquals(500, rack.devices.single().powerW)
        assertEquals("", rack.devices.single().serialNumber)
    }

    @Test
    fun parses_one_u_switch_and_two_u_server() {
        val rack = RackJsonParser.parse(
            """
            {"cabinet_id":"K17","devices":[
              {"type":"交换机","bottom_u":43,"height_u":1,"confidence":0.98,"evidence":"右侧轨 U43"},
              {"type":"服务器","bottom_u":21,"height_u":2,"confidence":0.93,"evidence":"右侧轨 U21-U22"}
            ],"uncertain":[]}
            """.trimIndent(),
            "rack-17.jpg",
        )

        assertEquals("K17", rack.cabinetId)
        assertEquals(2, rack.devices.size)
        assertEquals(2, rack.devices.last().heightU)
    }

    @Test
    fun parses_transmission_router_and_custom_device_types() {
        val rack = RackJsonParser.parse(
            """
            {"cabinet_id":"K17","devices":[
              {"type":"传输设备","bottom_u":40,"height_u":1},
              {"type":"路由器","bottom_u":30,"height_u":1},
              {"type":"自定义设备","bottom_u":20,"height_u":1}
            ]}
            """.trimIndent(),
            "rack-17.jpg",
        )

        assertEquals(listOf("传输设备", "路由器", "自定义设备"), rack.devices.map { it.type })
    }

    @Test
    fun rejects_three_u_records_for_the_mobile_policy() {
        assertFailsWith<RackValidationException> {
            RackJsonParser.parse(
                """{"cabinet_id":"K17","devices":[{"type":"服务器","bottom_u":21,"height_u":3}]}""",
                "rack-17.jpg",
            )
        }
    }

    @Test
    fun rejects_overlapping_u_ranges() {
        assertFailsWith<RackValidationException> {
            RackJsonParser.parse(
                """
                {"cabinet_id":"K17","devices":[
                  {"type":"服务器","bottom_u":20,"height_u":2},
                  {"type":"服务器","bottom_u":21,"height_u":2}
                ]}
                """.trimIndent(),
                "rack-17.jpg",
            )
        }
    }

    @Test
    fun rejects_types_outside_the_mobile_registration_policy() {
        assertFailsWith<RackValidationException> {
            RackJsonParser.parse(
                """{"cabinet_id":"K17","devices":[{"type":"防火墙","bottom_u":10,"height_u":1}]}""",
                "rack-17.jpg",
            )
        }
    }

    @Test
    fun keeps_evidence_backed_optional_risk_candidates_from_the_model() {
        val rack = RackJsonParser.parse(
            """
            {"cabinet_id":"K17","devices":[{"type":"交换机","bottom_u":43,"height_u":1}],
             "risk_candidates":[{"category":"布线规范","description":"U39 区域线缆遮挡","level":"待核验","recommendation":"复核线缆走向","evidence":"右侧轨 U39","confidence":0.71,"data_source":"照片识别"}],
             "action_hints":{"short":"复核 U39","medium":"整理布线","long":"接入动环"}}
            """.trimIndent(),
            "rack-17.jpg",
        )

        assertEquals(1, rack.riskCandidates.size)
        assertEquals("布线规范", rack.riskCandidates.single().category)
        assertEquals("复核 U39", rack.actionHints.short)
    }
}

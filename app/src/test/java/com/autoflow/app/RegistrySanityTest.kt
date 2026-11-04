package com.autoflow.app

import com.autoflow.app.core.Registry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 注册表完整性：防止新增条目时出现重复类型、空名称、重复参数键。 */
class RegistrySanityTest {

    private fun assertUnique(items: List<com.autoflow.app.core.ItemSpec>, name: String) {
        val types = items.map { it.type }
        assertEquals("$name 存在重复 type", types.size, types.toSet().size)
        items.forEach { spec ->
            assertTrue("$name 有条目缺少名称: ${spec.type}", spec.label.isNotBlank())
            assertTrue("$name 有条目缺少描述: ${spec.type}", spec.description.isNotBlank())
            val keys = spec.params.map { it.key }
            assertEquals(
                "$name/${spec.type} 参数键重复",
                keys.size,
                keys.toSet().size,
            )
        }
    }

    @Test
    fun catalogIsConsistent() {
        assertTrue("触发器数量过少", Registry.triggers.size >= 90)
        assertTrue("动作数量过少", Registry.actions.size >= 150)
        assertTrue("约束数量过少", Registry.constraints.size >= 55)
        assertUnique(Registry.triggers, "触发器")
        assertUnique(Registry.actions, "动作")
        assertUnique(Registry.constraints, "约束")
    }
}

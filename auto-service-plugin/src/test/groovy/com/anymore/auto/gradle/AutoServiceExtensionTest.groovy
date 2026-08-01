package com.anymore.auto.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class AutoServiceExtensionTest {

    @Test
    void '同一服务接口可累计多个必选别名'() {
        def extension = new AutoServiceExtension(false, new HashMap<String, Set<String>>())

        extension.require('example.Payment', 'wechat')
        extension.require('example.Payment', 'alipay')
        extension.require('example.Payment', 'wechat')

        assertEquals(['wechat', 'alipay'] as Set, extension.requireServices['example.Payment'])
    }

    @Test
    void '相同排除规则只保留一份'() {
        def extension = new AutoServiceExtension(false, new HashMap<String, Set<String>>())

        extension.exclude('example\\..*', 'beta')
        extension.exclude('example\\..*', 'beta')

        assertEquals(1, extension.exclusiveRules.size())
        def rule = extension.exclusiveRules.first()
        assertEquals('example\\..*', rule.className)
        assertEquals('beta', rule.alias)
    }

    @Test
    void '服务元素按优先级和类名稳定排序'() {
        def elements = [
                new AutoServiceRegisterAction.Element('example.Zebra', 0, '', false),
                new AutoServiceRegisterAction.Element('example.Alpha', 0, '', false),
                new AutoServiceRegisterAction.Element('example.Later', 5, '', false),
                new AutoServiceRegisterAction.Element('example.First', -1, '', false)
        ]

        assertEquals(
                ['example.First', 'example.Alpha', 'example.Zebra', 'example.Later'],
                elements.sort().collect { it.name })
        assertTrue(elements[0].priority < elements[1].priority)
    }
}

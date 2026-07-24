<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Button, Card, Form, Input, Switch, message } from 'ant-design-vue'
import { api } from '../api'

const form = reactive({
  enabled: false,
  serviceUrl: '',
  modelName: '',
  apiKey: '',
})

const apiKeyMask = ref('')
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

const loadConfig = async () => {
  loading.value = true
  try {
    const { data } = await api.get('/ai-config')
    form.enabled = data.enabled
    form.serviceUrl = data.serviceUrl ?? ''
    form.modelName = data.modelName ?? ''
    apiKeyMask.value = data.apiKeyMask ?? ''
  } catch {
    message.error('加载 AI 配置失败')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    const { data } = await api.put('/ai-config', {
      enabled: form.enabled,
      serviceUrl: form.serviceUrl || null,
      modelName: form.modelName || null,
      apiKey: form.apiKey || null,
    })
    apiKeyMask.value = data.apiKeyMask ?? ''
    form.apiKey = ''
    message.success('AI 配置已保存')
  } catch (error: unknown) {
    const msg = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    message.error(msg || '保存 AI 配置失败')
  } finally {
    saving.value = false
  }
}

const testConnection = async () => {
  testing.value = true
  try {
    await api.post('/ai-config/test')
    message.success('连接测试成功')
  } catch (error: unknown) {
    const msg = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    message.error(msg || '连接测试失败')
  } finally {
    testing.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <section class="ai-config-shell">
    <Card :bordered="false" :loading="loading" class="ai-config-card">
      <Form :model="form" layout="vertical" class="ai-config-form">
        <Form.Item label="启用 AI 功能">
          <Switch v-model:checked="form.enabled" />
        </Form.Item>

        <Form.Item label="服务地址（OpenAI 兼容）">
          <Input v-model:value="form.serviceUrl" placeholder="例如：https://api.openai.com/v1" />
        </Form.Item>

        <Form.Item label="模型名称">
          <Input v-model:value="form.modelName" placeholder="例如：gpt-4o-mini" />
        </Form.Item>

        <Form.Item label="API Key">
          <Input.Password
            v-model:value="form.apiKey"
            :placeholder="apiKeyMask ? `当前：${apiKeyMask}（留空保留原值）` : '请输入 API Key'"
          />
        </Form.Item>

        <div class="form-actions">
          <Button :loading="testing" @click="testConnection">连接测试</Button>
          <Button type="primary" :loading="saving" @click="save">保存配置</Button>
        </div>
      </Form>
    </Card>
  </section>
</template>

<style scoped>
.ai-config-shell {
  width: 100%;
  max-width: 600px;
}

.ai-config-card {
  box-shadow: 0 8px 24px rgb(15 23 42 / 8%);
}

.form-actions {
  display: flex;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid rgb(226 232 240);
}
</style>

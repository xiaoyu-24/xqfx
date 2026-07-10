<script setup lang="ts">
import { reactive } from 'vue'

const filters = reactive({ keyword: '', department: '', requesterName: '', type: '', status: '', saveType: '' })
</script>

<template>
  <section class="list-page">
    <div class="filter-grid">
      <label>关键词 <input v-model="filters.keyword" placeholder="标题或需求内容"></label>
      <label>所属系统 <select><option>全部系统</option></select></label>
      <label>目标版本 <select><option>全部版本</option></select></label>
      <label>部门 <input v-model="filters.department" placeholder="请输入部门"></label>
      <label>填写人 <input v-model="filters.requesterName" placeholder="请输入填写人"></label>
      <label>需求状态 <select v-model="filters.status"><option value="">全部状态</option><option>待评估</option><option>已确认</option><option>开发中</option><option>暂停</option><option>已完成</option><option>已拒绝</option><option>已关闭</option></select></label>
      <label>类型 <select v-model="filters.type"><option value="">全部类型</option><option>BUG</option><option>需求</option></select></label>
      <label>保存类型 <select v-model="filters.saveType"><option value="">全部</option><option>正式需求</option><option>草稿</option></select></label>
    </div>
    <div class="filter-actions"><button class="primary" type="button">查询</button><button class="secondary" type="button">重置</button></div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>类型</th><th>需求标题</th><th>所属系统 / 版本</th><th>填写人 / 部门</th><th>状态</th><th>填写时间</th><th>操作</th></tr></thead>
        <tbody><tr><td colspan="7" class="empty">请设置筛选条件并查询需求列表</td></tr></tbody>
      </table>
    </div>
    <div class="pagination-placeholder">共 0 条　上一页　1　下一页</div>
  </section>
</template>

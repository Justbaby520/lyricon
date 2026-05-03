<script setup>
import DefaultTheme from 'vitepress/theme'
import { onMounted } from 'vue'

const { Layout } = DefaultTheme
const bgUrl = __WALLPAPER_URL__ || ''
const copyright = __WALLPAPER_COPYRIGHT__ || ''

onMounted(() => {
  if (bgUrl) {
    document.documentElement.style.setProperty('--vp-home-bg-image', `url(${bgUrl})`)
  }
})
</script>

<template>
  <Layout />
  <div v-if="copyright" class="wallpaper-credit">{{ copyright }}</div>
</template>

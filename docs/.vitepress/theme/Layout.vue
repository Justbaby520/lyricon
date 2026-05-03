<script setup>
import DefaultTheme from 'vitepress/theme'
import { onMounted, ref } from 'vue'

const { Layout } = DefaultTheme
const copyright = ref('')

async function fetchWallpaper() {
  try {
    const res = await fetch('https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1')
    const data = await res.json()
    const img = data.images[0]
    const url = `https://www.bing.com${img.url}`
    document.documentElement.style.setProperty('--vp-home-bg-image', `url(${url})`)
    copyright.value = img.copyright
  } catch {
    document.documentElement.style.setProperty('--vp-home-bg-image', 'none')
  }
}

onMounted(fetchWallpaper)
</script>

<template>
  <Layout />
  <div v-if="copyright" class="wallpaper-credit">{{ copyright }}</div>
</template>

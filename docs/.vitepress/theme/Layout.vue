<script setup>
import DefaultTheme from 'vitepress/theme'
import { onMounted } from 'vue'

const { Layout } = DefaultTheme

async function fetchWallpaper() {
  try {
    const res = await fetch('https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1')
    const data = await res.json()
    const url = `https://www.bing.com${data.images[0].url}`
    document.documentElement.style.setProperty('--vp-home-bg-image', `url(${url})`)
  } catch {
    document.documentElement.style.setProperty('--vp-home-bg-image', 'none')
  }
}

onMounted(fetchWallpaper)
</script>

<template>
  <Layout />
</template>

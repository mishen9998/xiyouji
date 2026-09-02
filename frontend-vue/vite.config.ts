import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve, sep } from 'path'
import fs from 'fs'

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'serve-illustrations',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/images/')) {
            const filePath = illustrationPath(req.url)
            if (filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
              const ext = filePath.split('.').pop()?.toLowerCase()
              const type = ext === 'jpg' || ext === 'jpeg' ? 'image/jpeg'
                : ext === 'png' ? 'image/png'
                : ext === 'svg' ? 'image/svg+xml'
                : 'application/octet-stream'
              res.setHeader('Content-Type', type)
              fs.createReadStream(filePath).pipe(res)
              return
            }
          }
          next()
        })
      },
      configurePreviewServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/images/')) {
            const filePath = illustrationPath(req.url)
            if (filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
              const ext = filePath.split('.').pop()?.toLowerCase()
              const type = ext === 'jpg' || ext === 'jpeg' ? 'image/jpeg'
                : ext === 'png' ? 'image/png'
                : ext === 'svg' ? 'image/svg+xml'
                : 'application/octet-stream'
              res.setHeader('Content-Type', type)
              fs.createReadStream(filePath).pipe(res)
              return
            }
          }
          next()
        })
      },
    },
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    // Keep frontend output isolated; the container build copies dist into the
    // bootstrap module. This avoids coupling npm builds to Maven source paths.
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: 'js/[name].js',
        chunkFileNames: 'js/[name].js',
        assetFileNames: 'css/[name].[ext]',
      },
    },
  },
})

/** 将浏览器百分号编码的中文 URL 安全地解析到插图根目录内。 */
function illustrationPath(url: string): string | null {
  const imageRoot = resolve(__dirname, '..', '插图')
  try {
    const requestPath = decodeURIComponent(url.split('?', 1)[0].slice('/images/'.length))
    const candidate = resolve(imageRoot, requestPath)
    const rootPrefix = imageRoot.endsWith(sep) ? imageRoot : imageRoot + sep
    const normalizedCandidate = candidate.toLocaleLowerCase()
    const normalizedRoot = rootPrefix.toLocaleLowerCase()
    return normalizedCandidate.startsWith(normalizedRoot) ? candidate : null
  } catch {
    return null
  }
}

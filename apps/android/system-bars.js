;(() => {
  if (window.__mcxdSystemBars) return
  window.__mcxdSystemBars = true
  const probe = document.createElement('div')
  probe.hidden = true
  probe.style.backgroundColor = 'var(--color-base-100)'
  document.body.appendChild(probe)
  const canvas = document.createElement('canvas')
  canvas.width = 2
  canvas.height = 1
  const context = canvas.getContext('2d', { willReadFrequently: true })
  let previous = ''
  let pending = false
  function update() {
    pending = false
    if (!context) return
    const base = getComputedStyle(probe).backgroundColor
    const header = document.querySelector('header')
    const top = header ? getComputedStyle(header).backgroundColor : base
    // Canvas resolves oklch/color-mix and composites the translucent header
    // over the panel background before passing ordinary RGB colors to Android.
    context.clearRect(0, 0, 2, 1)
    context.fillStyle = base
    context.fillRect(0, 0, 2, 1)
    context.fillStyle = top
    context.fillRect(0, 0, 1, 1)
    const pixels = context.getImageData(0, 0, 2, 1).data
    if (pixels[3] !== 255 || pixels[7] !== 255) return
    const colors = JSON.stringify([
      (pixels[0] << 16) | (pixels[1] << 8) | pixels[2],
      (pixels[4] << 16) | (pixels[5] << 8) | pixels[6],
    ])
    if (colors === previous) return
    previous = colors
    console.info(`MCXD_SYSTEM_BARS:${colors}`)
  }
  function schedule() {
    if (pending) return
    pending = true
    requestAnimationFrame(update)
  }
  new MutationObserver(schedule).observe(document.documentElement, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ['data-theme', 'style', 'class'],
  })
  update()
})()

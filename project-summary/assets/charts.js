(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  // ===== Chart 1: Character Radar =====
  var chartRadar = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });
  chartRadar.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true },
    legend: {
      data: ['孙悟空', '猪八戒', '沙僧', '白龙马', '唐三藏'],
      bottom: 0,
      textStyle: { color: muted, fontSize: 11 },
      itemWidth: 10, itemHeight: 10
    },
    radar: {
      indicator: [
        { name: '生命值', max: 100 },
        { name: '攻击力', max: 10 },
        { name: '防御力', max: 10 },
        { name: '敏捷度', max: 10 },
        { name: '回复力', max: 10 }
      ],
      shape: 'polygon',
      splitNumber: 5,
      axisName: { color: ink, fontSize: 12 },
      splitLine: { lineStyle: { color: rule } },
      splitArea: { areaStyle: { color: ['transparent', 'rgba(212,166,74,0.03)'] } },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [{
      type: 'radar',
      data: [
        { value: [75, 9, 5, 7, 3], name: '孙悟空', areaStyle: { color: 'rgba(212,166,74,0.15)' }, lineStyle: { color: accent, width: 2 } },
        { value: [85, 7, 7, 4, 6], name: '猪八戒', areaStyle: { color: 'rgba(196,69,105,0.12)' }, lineStyle: { color: accent2, width: 2 } },
        { value: [90, 5, 9, 5, 5], name: '沙僧', areaStyle: { color: 'rgba(139,135,163,0.1)' }, lineStyle: { color: muted, width: 2 } },
        { value: [70, 7, 5, 9, 4], name: '白龙马', areaStyle: { color: 'rgba(212,166,74,0.08)' }, lineStyle: { color: accent, width: 2, type: 'dashed' } },
        { value: [80, 4, 6, 5, 9], name: '唐三藏', areaStyle: { color: 'rgba(196,69,105,0.08)' }, lineStyle: { color: accent2, width: 2, type: 'dashed' } }
      ]
    }]
  });

  // ===== Chart 2: Cards by Character =====
  var chartCards = echarts.init(document.getElementById('chart-cards'), null, { renderer: 'svg' });
  chartCards.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true, formatter: '{b}: {c} 张 ({d}%)' },
    legend: {
      bottom: 0,
      textStyle: { color: muted, fontSize: 11 },
      itemWidth: 10, itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: bg2, borderWidth: 2 },
      label: {
        color: ink,
        fontSize: 12,
        formatter: '{b}\n{c} 张'
      },
      labelLine: { lineStyle: { color: rule } },
      data: [
        { value: 12, name: '孙悟空', itemStyle: { color: accent } },
        { value: 10, name: '猪八戒', itemStyle: { color: accent2 } },
        { value: 8, name: '沙僧', itemStyle: { color: '#6b8e9e' } },
        { value: 8, name: '白龙马', itemStyle: { color: '#8b7355' } },
        { value: 14, name: '唐三藏', itemStyle: { color: '#9b6b9e' } },
        { value: 28, name: '通用卡牌', itemStyle: { color: muted } }
      ]
    }]
  });

  // ===== Chart 3: Code Size =====
  var chartCode = echarts.init(document.getElementById('chart-code'), null, { renderer: 'svg' });
  chartCode.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
    grid: { left: '15%', right: '10%', top: '10%', bottom: '15%' },
    xAxis: {
      type: 'value',
      axisLabel: { color: muted, fontSize: 11 },
      axisLine: { lineStyle: { color: rule } },
      splitLine: { lineStyle: { color: rule } }
    },
    yAxis: {
      type: 'category',
      data: ['前端 TS', '前端 Vue', '测试代码', '后端 Java'],
      axisLabel: { color: ink, fontSize: 12 },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [{
      type: 'bar',
      data: [
        { value: 725, itemStyle: { color: accent2 } },
        { value: 2783, itemStyle: { color: accent } },
        { value: 878, itemStyle: { color: '#6b8e9e' } },
        { value: 4627, itemStyle: { color: '#8b7355' } }
      ],
      barWidth: '50%',
      label: { show: true, position: 'right', color: ink, fontSize: 12, formatter: '{c} 行' }
    }]
  });

  // ===== Chart 4: Work Distribution =====
  var chartWork = echarts.init(document.getElementById('chart-work'), null, { renderer: 'svg' });
  chartWork.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true, formatter: '{b}: {c} 项 ({d}%)' },
    legend: {
      bottom: 0,
      textStyle: { color: muted, fontSize: 11 },
      itemWidth: 10, itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: '60%',
      center: ['50%', '45%'],
      itemStyle: { borderColor: bg2, borderWidth: 2 },
      label: { color: ink, fontSize: 11, formatter: '{b}: {c}' },
      data: [
        { value: 5, name: '卡牌/内容扩展', itemStyle: { color: accent } },
        { value: 4, name: 'Bug 修复', itemStyle: { color: accent2 } },
        { value: 3, name: '架构演进', itemStyle: { color: '#6b8e9e' } },
        { value: 1, name: '移动端适配', itemStyle: { color: '#8b7355' } },
        { value: 1, name: '监控/CI', itemStyle: { color: '#9b6b9e' } }
      ]
    }]
  });

  // ===== Resize =====
  window.addEventListener('resize', function() {
    chartRadar.resize();
    chartCards.resize();
    chartCode.resize();
    chartWork.resize();
  });
})();

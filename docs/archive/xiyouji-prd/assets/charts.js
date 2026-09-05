(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bg3 = style.getPropertyValue('--bg3').trim();
  var success = style.getPropertyValue('--success').trim();
  var warning = style.getPropertyValue('--warning').trim();
  var danger = style.getPropertyValue('--danger').trim();

  // --- Chart: Test Coverage ---
  var chartEl = document.getElementById('chart-test-coverage');
  if (chartEl) {
    var chart = echarts.init(chartEl, null, { renderer: 'svg' });
    chart.setOption({
      animation: false,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        appendToBody: true,
        backgroundColor: bg3,
        borderColor: rule,
        textStyle: { color: ink }
      },
      legend: {
        data: ['已完成测试', '无测试'],
        textStyle: { color: muted },
        top: 5
      },
      grid: { left: '3%', right: '4%', bottom: '3%', top: 50, containLabel: true },
      xAxis: {
        type: 'value',
        max: 100,
        axisLabel: { color: muted, formatter: '{value}%' },
        axisLine: { lineStyle: { color: rule } },
        splitLine: { lineStyle: { color: rule, opacity: 0.3 } }
      },
      yAxis: {
        type: 'category',
        data: ['GameService', 'BattleService', 'MultiplayerBattle', 'AuthService', 'JwtUtil', 'Card', 'Enemy', 'GameCharacter'],
        axisLabel: { color: ink },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [
        {
          name: '已覆盖',
          type: 'bar',
          stack: 'total',
          data: [80, 75, 70, 90, 95, 85, 80, 85],
          itemStyle: { color: success }
        },
        {
          name: '未覆盖',
          type: 'bar',
          stack: 'total',
          data: [20, 25, 30, 10, 5, 15, 20, 15],
          itemStyle: { color: rule, opacity: 0.4 }
        }
      ]
    });
    window.addEventListener('resize', function() { chart.resize(); });
  }
})();

(function() {
    var style = getComputedStyle(document.documentElement);
    var accent = style.getPropertyValue('--accent').trim();
    var accent2 = style.getPropertyValue('--accent2').trim();
    var ink = style.getPropertyValue('--ink').trim();
    var muted = style.getPropertyValue('--muted').trim();
    var rule = style.getPropertyValue('--rule').trim();
    var bg2 = style.getPropertyValue('--bg2').trim();

    // --- Chart: Docker Container Image Size ---
    var chart1 = echarts.init(document.getElementById('chart-container-size'), null, { renderer: 'svg' });
    chart1.setOption({
        animation: false,
        tooltip: {
            trigger: 'item',
            appendToBody: true,
            formatter: '{b}: {c} MB ({d}%)'
        },
        legend: {
            orient: 'vertical',
            left: 'left',
            textStyle: { color: muted, fontSize: 12 },
            top: 'middle'
        },
        series: [{
            name: '镜像大小',
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['60%', '50%'],
            avoidLabelOverlap: false,
            itemStyle: {
                borderRadius: 8,
                borderColor: bg2,
                borderWidth: 2
            },
            label: {
                show: true,
                color: ink,
                fontSize: 12,
                formatter: '{b}\n{c} MB'
            },
            labelLine: {
                lineStyle: { color: rule }
            },
            data: [
                { value: 133, name: 'App-1 (JRE+JAR)', itemStyle: { color: accent } },
                { value: 133, name: 'App-2 (JRE+JAR)', itemStyle: { color: accent + 'cc' } },
                { value: 95, name: 'MySQL 8.0', itemStyle: { color: accent2 } },
                { value: 40, name: 'Redis 7', itemStyle: { color: '#27ae60' } },
                { value: 50, name: 'Prometheus', itemStyle: { color: '#e67e22' } },
                { value: 45, name: 'Grafana', itemStyle: { color: '#f39c12' } },
                { value: 25, name: 'Nginx', itemStyle: { color: '#3498db' } },
                { value: 60, name: 'Zipkin', itemStyle: { color: '#9b59b6' } }
            ]
        }]
    });
    window.addEventListener('resize', function() { chart1.resize(); });
})();

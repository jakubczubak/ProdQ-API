package com.example.prodqapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;

@RestController
public class HomeController {

    @Value("${login.url}")
    private String loginUrl;

    @GetMapping("/")
    public ResponseEntity<String> home() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        String uptimeFormatted = String.format("%02d:%02d:%02d", uptime / 3600, (uptime % 3600) / 60, uptime % 60);

        String message = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>ProdQ API</title>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        min-height: 100vh;
                        background: #050510;
                        font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        overflow: hidden;
                        position: relative;
                    }

                    /* Animated mesh gradient background */
                    .mesh-gradient {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%%;
                        height: 100%%;
                        background:
                            radial-gradient(ellipse at 0%% 0%%, rgba(99, 102, 241, 0.15) 0%%, transparent 50%%),
                            radial-gradient(ellipse at 100%% 0%%, rgba(236, 72, 153, 0.1) 0%%, transparent 50%%),
                            radial-gradient(ellipse at 100%% 100%%, rgba(34, 197, 94, 0.1) 0%%, transparent 50%%),
                            radial-gradient(ellipse at 0%% 100%%, rgba(59, 130, 246, 0.1) 0%%, transparent 50%%);
                        animation: meshMove 20s ease-in-out infinite;
                    }

                    @keyframes meshMove {
                        0%%, 100%% { transform: scale(1) rotate(0deg); }
                        25%% { transform: scale(1.1) rotate(1deg); }
                        50%% { transform: scale(1) rotate(0deg); }
                        75%% { transform: scale(1.1) rotate(-1deg); }
                    }

                    /* Particle canvas */
                    #particles {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%%;
                        height: 100%%;
                        pointer-events: none;
                    }

                    /* Grid overlay */
                    .grid-overlay {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%%;
                        height: 100%%;
                        background-image:
                            linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
                            linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px);
                        background-size: 50px 50px;
                        pointer-events: none;
                    }

                    .container {
                        position: relative;
                        z-index: 10;
                        text-align: center;
                        padding: 2rem;
                        max-width: 800px;
                        width: 100%%;
                    }

                    /* 3D Logo */
                    .logo-container {
                        margin-bottom: 2rem;
                        perspective: 1000px;
                    }

                    .logo {
                        font-size: 5rem;
                        font-weight: 800;
                        background: linear-gradient(135deg, #6366f1 0%%, #a855f7 25%%, #ec4899 50%%, #f43f5e 75%%, #6366f1 100%%);
                        background-size: 300%% 300%%;
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        background-clip: text;
                        letter-spacing: -3px;
                        animation: gradientShift 4s ease infinite;
                        text-shadow: 0 0 80px rgba(99, 102, 241, 0.5);
                        transform-style: preserve-3d;
                        transition: transform 0.3s ease;
                    }

                    .logo:hover {
                        transform: rotateX(10deg) rotateY(-10deg) scale(1.05);
                    }

                    @keyframes gradientShift {
                        0%% { background-position: 0%% 50%%; }
                        50%% { background-position: 100%% 50%%; }
                        100%% { background-position: 0%% 50%%; }
                    }

                    .subtitle {
                        font-size: 0.9rem;
                        color: #64748b;
                        font-weight: 400;
                        letter-spacing: 6px;
                        text-transform: uppercase;
                        margin-top: 0.5rem;
                        opacity: 0;
                        animation: fadeInUp 1s ease forwards 0.5s;
                    }

                    @keyframes fadeInUp {
                        from {
                            opacity: 0;
                            transform: translateY(20px);
                        }
                        to {
                            opacity: 1;
                            transform: translateY(0);
                        }
                    }

                    /* Main card with glassmorphism */
                    .main-card {
                        background: rgba(15, 15, 35, 0.6);
                        backdrop-filter: blur(40px);
                        border: 1px solid rgba(255, 255, 255, 0.08);
                        border-radius: 24px;
                        padding: 2.5rem;
                        margin: 2rem auto;
                        box-shadow:
                            0 0 0 1px rgba(255, 255, 255, 0.05),
                            0 25px 50px -12px rgba(0, 0, 0, 0.8),
                            0 0 100px rgba(99, 102, 241, 0.1);
                        opacity: 0;
                        animation: fadeInUp 1s ease forwards 0.7s;
                        position: relative;
                        overflow: hidden;
                    }

                    .main-card::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: -100%%;
                        width: 100%%;
                        height: 100%%;
                        background: linear-gradient(90deg, transparent, rgba(255,255,255,0.05), transparent);
                        animation: shimmer 3s infinite;
                    }

                    @keyframes shimmer {
                        0%% { left: -100%%; }
                        100%% { left: 100%%; }
                    }

                    /* Status section */
                    .status-section {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 1rem;
                        margin-bottom: 2rem;
                    }

                    .status-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.5rem;
                        background: rgba(34, 197, 94, 0.1);
                        border: 1px solid rgba(34, 197, 94, 0.3);
                        padding: 0.5rem 1.25rem;
                        border-radius: 50px;
                        position: relative;
                    }

                    .status-dot {
                        width: 8px;
                        height: 8px;
                        background: #22c55e;
                        border-radius: 50%%;
                        box-shadow: 0 0 20px #22c55e, 0 0 40px #22c55e;
                        animation: pulse-dot 2s ease-in-out infinite;
                    }

                    @keyframes pulse-dot {
                        0%%, 100%% { transform: scale(1); opacity: 1; }
                        50%% { transform: scale(1.2); opacity: 0.8; }
                    }

                    .status-text {
                        color: #22c55e;
                        font-size: 0.75rem;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 2px;
                    }

                    /* Uptime display */
                    .uptime-display {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.5rem;
                        background: rgba(255, 255, 255, 0.02);
                        border: 1px solid rgba(255, 255, 255, 0.05);
                        border-radius: 12px;
                        padding: 0.75rem 1.5rem;
                        margin-bottom: 1.5rem;
                    }

                    .uptime-icon {
                        font-size: 1rem;
                    }

                    .uptime-value {
                        font-family: 'JetBrains Mono', monospace;
                        font-size: 0.9rem;
                        font-weight: 500;
                        color: #94a3b8;
                    }

                    /* CTA Button */
                    .cta-container {
                        margin-top: 1.5rem;
                    }

                    .cta-button {
                        position: relative;
                        display: inline-flex;
                        align-items: center;
                        gap: 0.75rem;
                        background: linear-gradient(135deg, #6366f1 0%%, #8b5cf6 100%%);
                        color: white;
                        text-decoration: none;
                        padding: 1rem 2.5rem;
                        border-radius: 14px;
                        font-size: 0.95rem;
                        font-weight: 600;
                        letter-spacing: 0.5px;
                        transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                        box-shadow:
                            0 10px 40px -10px rgba(99, 102, 241, 0.5),
                            0 0 0 0 rgba(99, 102, 241, 0.5);
                        overflow: hidden;
                    }

                    .cta-button::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 100%%;
                        height: 100%%;
                        background: linear-gradient(135deg, #8b5cf6 0%%, #a855f7 100%%);
                        opacity: 0;
                        transition: opacity 0.3s ease;
                    }

                    .cta-button:hover {
                        transform: translateY(-4px) scale(1.02);
                        box-shadow:
                            0 20px 40px -10px rgba(99, 102, 241, 0.6),
                            0 0 0 4px rgba(99, 102, 241, 0.2);
                    }

                    .cta-button:hover::before {
                        opacity: 1;
                    }

                    .cta-button span,
                    .cta-button svg {
                        position: relative;
                        z-index: 1;
                    }

                    .cta-button svg {
                        width: 20px;
                        height: 20px;
                        transition: transform 0.3s ease;
                    }

                    .cta-button:hover svg {
                        transform: translateX(4px);
                    }

                    /* Footer */
                    .footer {
                        margin-top: 2rem;
                        opacity: 0;
                        animation: fadeInUp 1s ease forwards 1s;
                    }

                    .copyright {
                        color: #334155;
                        font-size: 0.7rem;
                        letter-spacing: 1px;
                    }

                    /* Decorative orbs */
                    .orb {
                        position: fixed;
                        border-radius: 50%%;
                        filter: blur(80px);
                        pointer-events: none;
                        animation: float 10s ease-in-out infinite;
                    }

                    .orb-1 {
                        width: 400px;
                        height: 400px;
                        background: rgba(99, 102, 241, 0.15);
                        top: -10%%;
                        left: -10%%;
                    }

                    .orb-2 {
                        width: 300px;
                        height: 300px;
                        background: rgba(236, 72, 153, 0.1);
                        bottom: -5%%;
                        right: -5%%;
                        animation-delay: -5s;
                    }

                    .orb-3 {
                        width: 200px;
                        height: 200px;
                        background: rgba(34, 197, 94, 0.1);
                        top: 50%%;
                        right: 10%%;
                        animation-delay: -2.5s;
                    }

                    @keyframes float {
                        0%%, 100%% { transform: translate(0, 0) scale(1); }
                        25%% { transform: translate(20px, -20px) scale(1.05); }
                        50%% { transform: translate(0, 20px) scale(1); }
                        75%% { transform: translate(-20px, -10px) scale(0.95); }
                    }

                    /* Responsive */
                    @media (max-width: 640px) {
                        .logo { font-size: 3.5rem; }
                    }
                </style>
            </head>
            <body>
                <!-- Background elements -->
                <div class="mesh-gradient"></div>
                <div class="grid-overlay"></div>
                <canvas id="particles"></canvas>
                <div class="orb orb-1"></div>
                <div class="orb orb-2"></div>
                <div class="orb orb-3"></div>

                <div class="container">
                    <div class="logo-container">
                        <div class="logo">ProdQ</div>
                        <div class="subtitle">Manufacturing Execution System</div>
                    </div>

                    <div class="main-card">
                        <div class="status-section">
                            <div class="status-badge">
                                <div class="status-dot"></div>
                                <span class="status-text">All Systems Operational</span>
                            </div>
                        </div>

                        <div class="uptime-display">
                            <span class="uptime-icon">⚡</span>
                            <span class="uptime-value">Uptime: %s</span>
                        </div>

                        <div class="cta-container">
                            <a href="%s" class="cta-button">
                                <span>Launch Application</span>
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                                </svg>
                            </a>
                        </div>
                    </div>

                    <div class="footer">
                        <div class="copyright">© 2025 ProdQ • Manufacturing Execution System</div>
                    </div>
                </div>

                <script>
                    // Particle animation
                    const canvas = document.getElementById('particles');
                    const ctx = canvas.getContext('2d');

                    function resize() {
                        canvas.width = window.innerWidth;
                        canvas.height = window.innerHeight;
                    }
                    resize();
                    window.addEventListener('resize', resize);

                    const particles = [];
                    const particleCount = 80;

                    for (let i = 0; i < particleCount; i++) {
                        particles.push({
                            x: Math.random() * canvas.width,
                            y: Math.random() * canvas.height,
                            vx: (Math.random() - 0.5) * 0.3,
                            vy: (Math.random() - 0.5) * 0.3,
                            size: Math.random() * 2 + 0.5,
                            opacity: Math.random() * 0.5 + 0.2
                        });
                    }

                    function animate() {
                        ctx.clearRect(0, 0, canvas.width, canvas.height);

                        particles.forEach((p, i) => {
                            p.x += p.vx;
                            p.y += p.vy;

                            if (p.x < 0 || p.x > canvas.width) p.vx *= -1;
                            if (p.y < 0 || p.y > canvas.height) p.vy *= -1;

                            ctx.beginPath();
                            ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
                            ctx.fillStyle = `rgba(148, 163, 184, ${p.opacity})`;
                            ctx.fill();

                            // Connect nearby particles
                            particles.slice(i + 1).forEach(p2 => {
                                const dx = p.x - p2.x;
                                const dy = p.y - p2.y;
                                const dist = Math.sqrt(dx * dx + dy * dy);

                                if (dist < 120) {
                                    ctx.beginPath();
                                    ctx.moveTo(p.x, p.y);
                                    ctx.lineTo(p2.x, p2.y);
                                    ctx.strokeStyle = `rgba(99, 102, 241, ${0.1 * (1 - dist / 120)})`;
                                    ctx.stroke();
                                }
                            });
                        });

                        requestAnimationFrame(animate);
                    }
                    animate();
                </script>
            </body>
            </html>
            """.formatted(uptimeFormatted, loginUrl);

        return new ResponseEntity<>(message, HttpStatus.OK);
    }
}

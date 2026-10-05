/*
 * Editor de zonas de difuminado (MED-07). Arrastrar sobre la foto dibuja un rectángulo; las zonas se
 * escriben en el campo de texto como "x,y,ancho,alto" en porcentajes, que es lo que recibe el servidor.
 * Sin JavaScript, el campo se completa a mano.
 */
(function () {
    'use strict';
    document.addEventListener('DOMContentLoaded', function () {
        var editor = document.querySelector('[data-blur-editor]');
        var input = document.querySelector('[data-blur-regions]');
        var clear = document.querySelector('[data-blur-clear]');
        if (!editor || !input) {
            return;
        }
        var image = editor.querySelector('img');
        var regions = parse(input.value);
        var start = null;
        var drawing = null;
        clear.hidden = false;

        function parse(text) {
            return text.split(';').map(function (part) {
                var n = part.split(',').map(function (v) { return parseFloat(v); });
                return n.length === 4 && n.every(function (v) { return !isNaN(v); }) ? n : null;
            }).filter(Boolean);
        }

        function write() {
            input.value = regions.map(function (r) {
                return r.map(function (v) { return v.toFixed(2); }).join(',');
            }).join('; ');
        }

        function box(r, extraClass) {
            var div = document.createElement('div');
            div.className = 'blur-editor__box' + (extraClass ? ' ' + extraClass : '');
            div.style.left = r[0] + '%';
            div.style.top = r[1] + '%';
            div.style.width = r[2] + '%';
            div.style.height = r[3] + '%';
            return div;
        }

        function render() {
            editor.querySelectorAll('.blur-editor__box').forEach(function (b) { b.remove(); });
            regions.forEach(function (r) { editor.appendChild(box(r)); });
        }

        function point(event) {
            var rect = image.getBoundingClientRect();
            var x = Math.min(Math.max(event.clientX - rect.left, 0), rect.width);
            var y = Math.min(Math.max(event.clientY - rect.top, 0), rect.height);
            return [x / rect.width * 100, y / rect.height * 100];
        }

        function current(event) {
            var p = point(event);
            return [Math.min(start[0], p[0]), Math.min(start[1], p[1]), Math.abs(p[0] - start[0]), Math.abs(p[1] - start[1])];
        }

        editor.addEventListener('pointerdown', function (event) {
            event.preventDefault();
            start = point(event);
            editor.setPointerCapture(event.pointerId);
        });
        editor.addEventListener('pointermove', function (event) {
            if (!start) {
                return;
            }
            if (drawing) {
                drawing.remove();
            }
            drawing = box(current(event), 'is-drawing');
            editor.appendChild(drawing);
        });
        editor.addEventListener('pointerup', function (event) {
            if (!start) {
                return;
            }
            var r = current(event);
            start = null;
            if (drawing) {
                drawing.remove();
                drawing = null;
            }
            if (r[2] > 1 && r[3] > 1) {
                regions.push(r);
                write();
                render();
            }
        });
        input.addEventListener('change', function () {
            regions = parse(input.value);
            render();
        });
        clear.addEventListener('click', function () {
            regions = [];
            write();
            render();
        });
        render();
    });
})();

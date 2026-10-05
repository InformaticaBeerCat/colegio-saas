/*
 * Menú del sitio público (ACC-04). Mejora progresiva: sin JavaScript el menú se ve completo y los
 * submenús se abren al pasar el mouse o al llegar con el tabulador. Con JavaScript, el menú se pliega
 * en el teléfono y cada submenú se abre con su botón; Escape lo cierra y devuelve el foco.
 */
(function () {
    'use strict';
    document.documentElement.classList.add('js');

    document.addEventListener('DOMContentLoaded', function () {
        var nav = document.querySelector('.site-nav');
        if (!nav) {
            return;
        }
        var toggle = nav.querySelector('.nav-toggle');
        toggle.hidden = false;
        toggle.addEventListener('click', function () {
            var open = toggle.getAttribute('aria-expanded') !== 'true';
            toggle.setAttribute('aria-expanded', String(open));
            nav.classList.toggle('is-open', open);
        });

        var submenuToggles = nav.querySelectorAll('.submenu-toggle');
        function closeAll(except) {
            submenuToggles.forEach(function (button) {
                if (button !== except) {
                    button.setAttribute('aria-expanded', 'false');
                    document.getElementById(button.getAttribute('aria-controls')).classList.remove('is-open');
                }
            });
        }
        submenuToggles.forEach(function (button) {
            button.hidden = false;
            var submenu = document.getElementById(button.getAttribute('aria-controls'));
            button.addEventListener('click', function () {
                var open = button.getAttribute('aria-expanded') !== 'true';
                closeAll(button);
                button.setAttribute('aria-expanded', String(open));
                submenu.classList.toggle('is-open', open);
            });
            submenu.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') {
                    closeAll(null);
                    button.focus();
                }
            });
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                var opened = nav.querySelector('.submenu-toggle[aria-expanded="true"]');
                if (opened) {
                    closeAll(null);
                    opened.focus();
                }
            }
        });
        document.addEventListener('click', function (event) {
            if (!nav.contains(event.target)) {
                closeAll(null);
            }
        });
    });
})();

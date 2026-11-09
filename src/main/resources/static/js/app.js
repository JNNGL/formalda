(function () {
    'use strict';

    var toggle = document.querySelector('[data-menu-toggle]');
    var navigation = document.getElementById('main-navigation');

    if (toggle && navigation) {
        toggle.addEventListener('click', function () {
            var expanded = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', String(!expanded));
            navigation.classList.toggle('is-open', !expanded);
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && navigation.classList.contains('is-open')) {
                navigation.classList.remove('is-open');
                toggle.setAttribute('aria-expanded', 'false');
                toggle.focus();
            }
        });
    }

    document.querySelectorAll('[data-auto-submit]').forEach(function (control) {
        control.addEventListener('change', function () {
            if (control.form) {
                control.form.requestSubmit();
            }
        });
    });

    document.querySelectorAll('[data-count-target]').forEach(function (field) {
        var target = document.getElementById(field.getAttribute('data-count-target'));
        if (!target) return;

        var update = function () {
            target.textContent = String(field.value.length);
        };
        field.addEventListener('input', update);
        update();
    });
}());

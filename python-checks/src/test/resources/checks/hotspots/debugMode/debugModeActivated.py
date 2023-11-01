############################################
###                Django                ###
############################################


def django_test():
    from django.conf import settings, mysettings
    settings.configure(DEBUG=True)  # Noncompliant
    settings.configure(DEBUG_PROPAGATE_EXCEPTIONS=True)  # Noncompliant {{Make sure this debug feature is deactivated before delivering the code in production.}}
    #                  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    mysettings.configure(DEBUG=True)  # OK
    settings.otherFn(DEBUG=True)  # OK
    settings.configure()

    args = {'DEBUG': True}
    settings.configure(**args) # FN No implementation is done currently for the unpacking expression.

    configure(DEBUG=True) # OK
    configure() # OK
    foo.configure(DEBUG=True) # OK

    def custom_config(config):
        settings.configure(default_settings=config, DEBUG=True)  # Noncompliant

    settings.configure(DEBUG=False) # OK
    settings.configure(OTHER=False) # OK

    DEBUG = True  # OK, filename is not globalsetting.py nor settings.py

def flask_test():
    from flask import Flask
    app = Flask()

    app.debug = True  # Noncompliant

    app.config['DEBUG'] = True  # Noncompliant
    app.config.update({'DEBUG': True})  # FN This should be fixed as part of https://sonarsource.atlassian.net/browse/SONARPY-1541

    app.config['DEBUG'] = app.debug = True  # Noncompliant 2
#   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    app.conf['DEBUG'] = app.debug = True  # Noncompliant
#   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    app.run(debug=True)  # Noncompliant
#           ^^^^^^^^^^
    app.run('0.0.0.0', 8080, True)  # Noncompliant
#                            ^^^^

    app.config['DEBUG', 'debug'] = True

    app.config['DEBUG'] = False
    app.run(debug=False)
    app.run()

    app_not_defined.run(debug=True)

def func():
  try:
    foo()
  except SomeError as e:
    raise (e) # Noncompliant {{Add logic to this except clause or eliminate it and rethrow the exception automatically.}}
#   ^^^^^^^^^

  try:
    foo()
  except SomeError as e:
    bar()
    raise (e)

  try:
    foo()
  except SomeError:
    raise # Noncompliant {{Add logic to this except clause or eliminate it and rethrow the exception automatically.}}
#   ^^^^^

  try:
    foo()
  except (InvalidResultError, EmptyResult):
    raise # Noncompliant {{Add logic to this except clause or eliminate it and rethrow the exception automatically.}}
#   ^^^^^

  try:
    foo()
  except (InvalidResultError, EmptyResult) as e:
    raise e # Noncompliant {{Add logic to this except clause or eliminate it and rethrow the exception automatically.}}
#   ^^^^^^^

  try:
    foo()
  except ValueError:
    raise ValueError # ok, raises a different instance of the exception

  try:
    foo()
  except SomeError:
    raise AnotherError

  try:
    foo()
  except:
    raise TypeError("Some error")

  try:
    foo()
  except FieldDoesNotExist as e:
    raise IncorrectLookupParameters(e) from e

# Python 2
# https://www.python.org/dev/peps/pep-3109/
  try:
    foo()
  except (E, V) as e:
    raise E, V

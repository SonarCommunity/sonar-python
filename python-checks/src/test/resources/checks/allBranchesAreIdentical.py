def func():
  if b == 0:  # Noncompliant {{Remove this if statement or edit its code blocks so that they're not all the same.}}
# ^^
    doOneMoreThing()
  elif b == 1:
    doOneMoreThing()
  else:
    doOneMoreThing()

if b == 0:  # ok, exception when no else clause
  doOneMoreThing()
elif b == 1:
  doOneMoreThing()

if b == 0:  # ok, not all branches are the same
  doSomething()
elif b == 1:
  doSomethingElse()
else:
  doSomething()

if b == 0:  # ok, not all branches are the same
  doSomething()
elif b == 1:
  doSomething()
else:
  doSomethingElse()


if b == 0:  # ok
  doSomething()
elif b == 1:
  doSomethingElse()


a = 1 if x else 1 # Noncompliant

a = 1 if x else 1 if y else 1 # Noncompliant 2

a = 1 if x else 1 if y else 1 if z else 1 # Noncompliant 3

a = (1 if x else 1) if cond else (2 if x else 3) # Noncompliant
#      ^^
a = 1 if x else 2 if y else 1

a = 1 if x else 1 if y else 2

param = 2

if param == 1:
    print(1)
elif param == 2:
    print(2)
elif param == 1:            # Noncompliant {{This branch duplicates the one on line 3.}}
#    ^^^^^^^^^^
    print(3)

if param == 1:
    print(1)
else:
    if param == 2:
        print(2)
    else:
        if param == 2:            # Noncompliant [[secondary=-3]]
            print(3)


if param == 1:
    print(1)
else:
    if param == 2:
        print(2)
    elif param == 1:            # Noncompliant
            print(3)


if param == 1:
    print(1)
else:
    print(2)
    if param == 1:
        print(2)
    elif param == 1:  # Noncompliant
        print(3)
    print(2)

if param == 1:
    if param > 0:
        print(1)
elif param == 2:
    if param > 0:  # Compliant
        print 2
elif param == 3:
    if param > 0:  # Compliant
        print(2)

if foo is None and bar is None:
    print(1)
elif foo is not None and bar is None:
    print(2)
elif foo is not None and bar is not None:
    print(3)

if foo() < 1:
    print(1)
elif foo() > 1:
    print(2)

if foo() == 1:
    print(3)
elif foo() > 1:
    print(4)

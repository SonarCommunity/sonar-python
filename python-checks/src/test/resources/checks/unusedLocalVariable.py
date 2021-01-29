unread_global = 1

def f(unread_param):
    global unread_global
    unread_global = 1
    unread_local = 1 # Noncompliant
    unread_local = 2 # Noncompliant
    read_local = 1
    print(read_local)
    read_in_nested_function = 1
    def nested_function():
        print(read_in_nested_function)

def using_locals(a, b):
  c = a + b
  # "locals" will include the "c" value
  return locals()

def unused_class():
    class A: pass # OK

def string_interpolation():
    value1 = 1
    value2 = 2
    value3 = 3 # Noncompliant
    value4 = 4 # Noncompliant
    value5 = 1
    foo(F'{value5} foo')
    value6 = ''
    print(f"{'}' + value6}")
    value7 = ''
    printf(rf'{value7}')
    value8 = 10
    value9 = 10
    print(f"{3.14159265358979:{value8}.{value9 * 5}}")
    return f'{value1}, {2*value2}, value3bis, value4'

def function_with_lambdas():
    print([(lambda unread_lambda_param: 2)(i) for i in range(10)])
    x = 42 # Noncompliant
    print([(lambda x: x*x)(i) for i in range(10)])
    y = 42
    print([(lambda x: x*x + y)(i) for i in range(10)])
    {y**2 for a in range(3) if lambda x: x > 1 and y > 1} # Noncompliant
#             ^

def using_tuples():
    x, y = (1, 2)
    print x
    (a, b) = (1, 2)
    print b

    for name, b in foo():
        pass
    for (c, d) in foo():
        pass

def for_loops():
    for _ in range(10):
        do_something()
    for j in range(10): # Noncompliant
        do_something()
    for i in range(10):
        do_something(i)

def unused_import():
    import foo        # OK, should be handled in a dedicated rule
    from x import y   # OK, should be handled in a dedicated rule

def no_fp_type_annotation():
    value: str  # OK

def no_fp_type_annotation_2():
    value: str  # OK
    return [int(value) for value in something()]

def no_fn_type_annotation_with_assignment():
    value: str = "hello"  # Noncompliant
    return [int(value) for value in something()]

def no_fp_f_string_conditional_expr(p):
    x = 42 # OK
    f"{x if p != 0 else 0}"

def no_fp_f_string_conditional_expr_2(p):
    x = 42 # OK
    f"something {x if p != 0 else 0} something"

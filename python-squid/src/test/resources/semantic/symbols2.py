def function_with_local():
    a = 11
    a += 1
    a.x = 1
    foo(a)
    t2: str = "abc"
    b.a = 1
    foo().x *= 1
    foo().x : int = 1

x = 10

def function_with_free_variable():
    foo(x) # x is a free variable, not in local variable


def function_with_rebound_variable():
    foo(x) # x is bound, it's in local variable
    x = 1

def simple_parameter(a):
    pass
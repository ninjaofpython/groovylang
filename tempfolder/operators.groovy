// Arithmetic operators
// Normal Arithmetic
assert 1 + 2 == 3
assert 4 - 3 == 1
assert 3 * 5 == 15
assert 3 / 2 == 1.5
assert 10 % 3 == 1
assert 2 ** 3 == 8

assert 9.intdiv(3) == 3
assert 2.2.plus(1.1) == 3.3

// Unary operators
assert +3 == 3
assert -4 == 0 - 4
assert -(-1) == 1

// Increment ++
// Decrement --
int i = 10
println (++i)
println i
int j = 20
println (--j)
println j

def a = 2
def b = a++ * 3
assert a == 3 & b == 6
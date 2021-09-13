def str = "Hello"
def myClosure1 = {
    name -> println("${str} ${name}")
}
myClosure1.call("Raghav")

def myMethod(clos) {
    clos.call("Groovy")
}
myMethod(myClosure1)

def myClosure2 = {
    a, b, c ->
    return (a + b + c)
}
def res = myClosure2.call(10, 20, 30)
println(res)
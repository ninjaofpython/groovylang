def name = "Saa"
println "My name is " + name
println "Name is ${name}"
def x = 10
def X = 20
println x + X

// Dynamically type example
def (String a, int b, Double c) = [30, 40, 50]
println a
println b
println c
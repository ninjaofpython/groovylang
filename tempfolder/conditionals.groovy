def num = 11
// if/else statement
if (num > 0){
    println("Number is positive")
}
else {
    println("Number is negative")
}

// if/elseif/else statement
if (num == 12){
    println("Number is 12")
}
else if (num == 13){
    println("Number is 13")
}
else {
    println("Number is neither 12 or 13")
}

// Switch case
def x = "a"
def g = toString(x)
def result = ""

switch(g){
    case 0: 
        result = "x is zero"
        break
    case {x>0}:
        result = "x is positive"
        break
    case {x<0}:
        result = "x is negative"
        break
    default:
        result = "Invalid number"
}
println result
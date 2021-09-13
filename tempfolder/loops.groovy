// for loop
for(int i=1; i<5; i++){
    println("for loop" + i)
}

// for in loop
for(a in 1..5){
    println a
}

// upto loop
1.upto(5)
{
    println "$it"
}

// for loop with a list
for (i in [0, 1, 2, 3, 4]){
    println("My list loop" + i)
}
if True:<fold text='...'>
    pass</fold>
else:<fold text='...'>
    pass</fold>

if True:<fold text='...'>
    pass
    pass</fold>
elif True:<fold text='...'>
    pass
    pass</fold>
else:<fold text='...'>
    pass
    pass</fold>


x  = []
for i in x:<fold text='...'>
    pass</fold>

for i in x:<fold text='...'>
    pass
    pass</fold>

while True:<fold text='...'>
    pass
    pass</fold>

f = open('1.txt')
ints = []
try:<fold text='...'>
    for line in f:<fold text='...'>
        ints.append(int(line))
        ints.append(int(line))</fold></fold>

except ValueError:<fold text='...'>
    print('')
    print('')
    print('')
    print('')
    print('')</fold>
except Exception:<fold text='...'>
    print('')</fold>
else:<fold text='...'>
    print('')
    print('')
    print('')
    print('')</fold>
finally:<fold text='...'>
    f.close()
    print('')</fold>
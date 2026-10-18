package rulescript_test

import "testing"

func TestSimplifyFalseOr(t *testing.T) {
	rule := loadRule(t, "SimplifyFalseOr")
	e := newEnv(t)

	input1, cols1 := e.scan("t", 3)
	predicate1 := e.f.ConstructEq(e.v(cols1[0]), e.num(11))
	before1 := e.f.ConstructSelect(input1, e.filters(e.f.ConstructOr(e.f.ConstructFalse(), predicate1)))
	want1 := e.f.ConstructSelect(input1, e.filters(predicate1))
	e.expectRewrite(rule, before1, want1)

	input2, cols2 := e.scan("u", 2)
	predicate2 := e.f.ConstructEq(e.v(cols2[1]), e.num(29))
	before2 := e.f.ConstructSelect(input2, e.filters(e.f.ConstructOr(e.f.ConstructFalse(), predicate2)))
	want2 := e.f.ConstructSelect(input2, e.filters(predicate2))
	e.expectRewrite(rule, before2, want2)

	nearInput, nearCols := e.scan("t", 3)
	nearPredicate := e.f.ConstructEq(e.v(nearCols[0]), e.num(11))
	nearMiss := e.f.ConstructSelect(nearInput, e.filters(e.f.ConstructOr(nearPredicate, e.f.ConstructFalse())))
	e.expectNoMatch(rule, nearMiss)
}

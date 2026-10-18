package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFoldNonNullTupleIsTupleNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNonNullTupleIsTupleNotNull")
	e := newEnv(t)
	s, c := e.scan("nn", 2)
	notNullA := e.f.ConstructIsNot(e.v(c[0]), memo.NullSingleton)
	notNullB := e.f.ConstructIsNot(e.v(c[1]), memo.NullSingleton)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(notNullA, notNullB)))
	e.expectRewrite(rule, before, s)

	nullable, nc := e.scan("t", 2)
	nullableA := e.f.ConstructIsNot(e.v(nc[0]), memo.NullSingleton)
	nullableB := e.f.ConstructIsNot(e.v(nc[1]), memo.NullSingleton)
	e.expectNoMatch(rule, e.f.ConstructSelect(nullable, e.filters(e.f.ConstructAnd(nullableA, nullableB))))

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(
		e.f.ConstructIs(e.v(c[0]), memo.NullSingleton),
		e.f.ConstructIs(e.v(c[1]), memo.NullSingleton),
	))))

	one, oc := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(one, e.filters(e.f.ConstructIsNot(e.v(oc[0]), memo.NullSingleton))))
}

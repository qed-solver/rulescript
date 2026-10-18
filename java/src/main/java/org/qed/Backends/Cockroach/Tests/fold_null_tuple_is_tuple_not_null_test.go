package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullTupleIsTupleNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNullTupleIsTupleNotNull")
	e := newEnv(t)
	in, cols := e.scan("t", 3)
	nullCheck := e.f.ConstructIsNot(e.f.ConstructNull(types.Int), memo.NullSingleton)
	colCheck := e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)
	before := e.f.ConstructSelect(in, e.filters(e.f.ConstructAnd(nullCheck, colCheck)))
	want := e.f.ConstructSelect(in, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(in, e.filters(colCheck)))
	e.expectNoMatch(rule, e.f.ConstructSelect(in, e.filters(e.f.ConstructAnd(
		e.f.ConstructIsNot(e.num(1), memo.NullSingleton), colCheck))))
	e.expectNoMatch(rule, e.f.ConstructSelect(in, e.filters(e.f.ConstructAnd(
		e.f.ConstructIs(e.f.ConstructNull(types.Int), memo.NullSingleton), colCheck))))
}

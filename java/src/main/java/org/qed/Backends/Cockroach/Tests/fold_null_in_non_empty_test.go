package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullInNonEmpty(t *testing.T) {
	rule := loadRule(t, "FoldNullInNonEmpty")
	e := newEnv(t)
	src, cols := e.scan("t", 2)
	a, b := e.v(cols[0]), e.v(cols[1])
	null := e.f.ConstructNull(types.Int)
	nullBool := e.f.ConstructNull(types.Bool)
	inTuple := e.f.ConstructOr(
		e.f.ConstructEq(null, a),
		e.f.ConstructEq(null, b),
	)
	before := e.f.ConstructSelect(src, e.filters(inTuple))
	want := e.f.ConstructSelect(src, e.filters(nullBool))
	e.expectRewrite(rule, before, want)

	wrongOp := e.f.ConstructOr(
		e.f.ConstructEq(null, a),
		e.f.ConstructLt(null, b),
	)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(wrongOp)))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructEq(null, a))))
}

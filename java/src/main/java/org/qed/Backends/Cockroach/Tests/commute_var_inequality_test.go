package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCommuteVarInequality(t *testing.T) {
	rule := loadRule(t, "CommuteVarInequality")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	x := e.v(lc[0])
	y := e.v(rc[0])
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructLe(x, y)))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructGe(y, x)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{}), e.filters(e.f.ConstructLe(x, y))))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructLt(x, y))))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructLe(y, x))))
}

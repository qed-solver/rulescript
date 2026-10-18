package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCommuteConst(t *testing.T) {
	rule := loadRule(t, "CommuteConst")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(rc[0]), e.v(lc[0]))))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))))
	leftJoin := e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))))
}

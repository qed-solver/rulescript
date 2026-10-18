package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCommuteConstInequality(t *testing.T) {
	rule := loadRule(t, "CommuteConstInequality")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(rc[0]), e.v(lc[0]))))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructGt(e.v(lc[0]), e.v(rc[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructLe(e.v(lc[0]), e.v(rc[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{}), e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}), e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))))
}

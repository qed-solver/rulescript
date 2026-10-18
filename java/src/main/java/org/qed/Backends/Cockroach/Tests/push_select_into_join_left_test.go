package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoJoinLeft(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushSelectIntoJoinLeft")
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	lv := e.v(lc[0])
	rv := e.v(rc[0])
	leftFilter := e.f.ConstructEq(lv, e.num(11))
	unboundFilter := e.f.ConstructEq(lv, rv)
	on := e.f.ConstructEq(lv, e.num(29))
	join := e.f.ConstructLeftJoin(l, r, e.filters(on), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(leftFilter, unboundFilter))
	leftInput := e.f.ConstructSelect(l, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(11))))
	wantJoin := e.f.ConstructLeftJoin(leftInput, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(29))), &memo.JoinPrivate{})
	want := e.f.ConstructSelect(wantJoin, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))))
	e.expectRewrite(rule, before, want)

	nearMissJoin := e.f.ConstructLeftJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(29))), &memo.JoinPrivate{})
	nearMiss := e.f.ConstructSelect(nearMissJoin, e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(11))))
	e.expectRewrite(rule, nearMiss, e.f.ConstructLeftJoin(leftInput, r, e.filters(on), &memo.JoinPrivate{}))
	innerJoin := e.f.ConstructInnerJoin(l, r, e.filters(on), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(innerJoin, e.filters(leftFilter, unboundFilter)))
}
